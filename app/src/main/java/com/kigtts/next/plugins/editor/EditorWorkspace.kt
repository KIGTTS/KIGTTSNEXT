package com.kigtts.next.plugins.editor

import android.content.Context
import android.net.Uri
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.state.EDITOR_MANAGER_PAGE
import com.kigtts.next.state.NATIVE_BUILDER_ID
import com.kigtts.next.state.PLUGIN_EDITOR_ID
import com.kigtts.plugin.api.AppCommand
import com.kigtts.plugin.api.AppReply
import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.runtime.PluginManager
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal const val EDITOR_SIGNING_PAGE = "editor-signatures"

internal class EditorWorkspace(
    private val context: Context, private val manager: PluginManager
) {
    val presets = EditorPresets(context, manager)
    val signatures = EditorSigningVault(context)

    suspend fun create(model: AppViewModel, language: String, presetId: String?, name: String, id: String, author: String) {
        require(language in setOf("lua", "java", "kotlin") && name.length in 1..64 && validEditorPackageName(id) && author.length <= 120)
        requireAvailablePackageName(id)
        val owner = if (language == "lua") PLUGIN_EDITOR_ID else NATIVE_BUILDER_ID
        require(manager.isVerifiedBundle(owner) && manager.registry.plugin(owner) != null)
        val preset = presetId?.let(presets::byId)
        require(preset == null || preset.language == language)
        val path = preset?.let { presets.stage(it, id, name, author) }
        withContext(Dispatchers.Main) {
            if (path == null) {
                model.invoke(owner, "editor-create", mapOf("name" to name, "id" to id, "author" to author, "language" to language))
            } else {
                model.invoke(owner, "editor-preset", mapOf("resourcePath" to path))
            }
        }
    }

    private suspend fun requireAvailablePackageName(id: String) = withContext(Dispatchers.IO) {
        require(validEditorPackageName(id))
        require(manager.packages.installed(id) == null &&
            manager.paths.existingResource(PLUGIN_EDITOR_ID, "projects/$id/code/manifest.json") == null &&
            manager.paths.existingResource(NATIVE_BUILDER_ID, "projects/$id/plugin-spec.json") == null) {
            "Package name is already in use"
        }
    }

    suspend fun author(owner: String, id: String): String = withContext(Dispatchers.IO) {
        val manifest = if (owner == PLUGIN_EDITOR_ID) "code/manifest.json" else "plugin-spec.json"
        val source = manager.paths.existingResource(owner, "projects/$id/$manifest") ?: return@withContext ""
        PluginJson.parseToJsonElement(source.readText()).jsonObject["author"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    suspend fun keys() = withContext(Dispatchers.IO) {
        signatures.migrateLegacy(manager)
        signatures.list()
    }

    suspend fun importSignature(uri: Uri, name: String, password: CharArray) = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
            val data = java.io.ByteArrayOutputStream()
            val block = ByteArray(4096)
            while (true) {
                val count = stream.read(block)
                if (count == -1) break
                require(data.size() + count <= 65_536)
                data.write(block, 0, count)
            }
            data.toByteArray()
        }
        try { signatures.import(name, bytes, password) } finally { bytes.fill(0); password.fill('\u0000') }
    }

    suspend fun exportSignature(uri: Uri, fingerprint: String, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val archive = signatures.export(fingerprint, password)
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(archive) }
        } finally { password.fill('\u0000') }
    }

    suspend fun request(model: AppViewModel?, owner: String, call: AppCommand.ManagedCall): AppReply = try {
        val reply = when (call.channel) {
            "editor-signing" -> sign(owner, call)
            "editor-template" -> template(requireNotNull(model), owner, call.command)
            else -> error("Unknown editor channel")
        }
        AppReply.Data(reply)
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) { AppReply.Failure("EDITOR_REQUEST_FAILED", error.message?.take(250) ?: "Editor request failed") }

    private suspend fun sign(owner: String, request: AppCommand.ManagedCall): JsonObject = withContext(Dispatchers.IO) {
        require(owner in setOf(PLUGIN_EDITOR_ID, NATIVE_BUILDER_ID) && manager.isVerifiedBundle(owner))
        manager.paths.validatePackageId(request.projectId)
        val raw = Base64.getDecoder().decode(request.command.getValue("manifest").jsonPrimitive.content)
        require(raw.size in 1..96 * 1024)
        val manifest = PluginJson.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        require(manifest.getValue("id").jsonPrimitive.content == request.projectId && manifest["files"] is JsonObject &&
            manifest.getValue("runtime").jsonPrimitive.content == "android-dex")
        val selected = signatures.selected(owner, request.projectId) ?: return@withContext buildJsonObject { put("default", true) }
        require(request.command["interactive"]?.jsonPrimitive?.booleanOrNull != false) { "Select Build in the editor to use this signature" }
        val signed = signatures.sign(selected, raw)
        val base64 = Base64.getEncoder()
        buildJsonObject {
            put("publicKey", base64.encodeToString(signed.publicKey))
            put("signature", base64.encodeToString(signed.signature))
        }
    }

    private suspend fun template(model: AppViewModel, owner: String, input: JsonObject): JsonObject {
        require(owner == "tools.cloud-agent" && manager.isVerifiedBundle(owner))
        fun field(key: String) = input[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val operation = field("operation")
        if (operation == "list") return presets.describe()
        val preset = presets.byId(field("preset"))
        return when (operation) {
            "files" -> buildJsonObject { put("files", presets.describeFiles(preset)) }
            "read" -> presets.read(preset, field("path"), input["offset"]?.jsonPrimitive?.intOrNull ?: 0)
            "create" -> {
                require(manager.isVerifiedBundle(preset.owner) && manager.registry.plugin(preset.owner) != null) {
                    model.hostCopy("editor.enable_first")
                }
                val id = field("packageName")
                requireAvailablePackageName(id)
                val path = presets.stage(preset, id, field("name").trim(), field("author").trim())
                withContext(Dispatchers.Main) {
                    require(preset.owner !in model.state.value.busyPlugins)
                    model.navigate(Destination.PluginPage(PLUGIN_EDITOR_ID, EDITOR_MANAGER_PAGE))
                    model.invoke(preset.owner, "editor-preset", mapOf("resourcePath" to path))
                }
                buildJsonObject { put("pending", true); put("packageName", id) }
            }
            else -> error("Unknown editor template operation")
        }
    }
}
