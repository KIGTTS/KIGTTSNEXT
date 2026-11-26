package com.kigtts.next.plugins.editor

import android.content.Context
import com.kigtts.plugin.api.PLUGIN_API_VERSION
import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.runtime.PluginManager
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable internal data class EditorPreset(
    val id: String, val language: String, val owner: String, val archive: String, val version: String,
    val apiVersion: Int, val name: String, val exampleId: String, val manifest: String, val sha256: String
)

@Serializable private data class EditorPresetIndex(val schema: Int, val sourceRevision: String, val items: List<EditorPreset>)

private val editorPackageName = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){1,7}")
internal fun validEditorPackageName(value: String) = value.length in 3..80 && editorPackageName.matches(value) &&
    !value.startsWith("runtime.") && !value.startsWith("tools.") && !value.startsWith("service.")

internal class EditorPresets(private val application: Context, private val manager: PluginManager) {
    private val index by lazy {
        application.assets.open("editor-presets/index.json").bufferedReader().use { reader ->
            PluginJson.decodeFromString<EditorPresetIndex>(reader.readText()).also {
                require(it.schema == 1 && it.items.all { item -> item.apiVersion == PLUGIN_API_VERSION })
            }
        }
    }
    val items: List<EditorPreset> get() = index.items

    fun describe() = buildJsonObject {
        put("sourceRevision", index.sourceRevision)
        put("items", PluginJson.encodeToJsonElement(items))
    }

    suspend fun describeFiles(preset: EditorPreset) = withContext(Dispatchers.IO) {
        buildJsonArray {
            fun addFile(name: String, bytes: ByteArray) {
                add(buildJsonObject { put("path", name); put("bytes", bytes.size); put("text", textExtension(name)) })
            }
            entries(preset, ::addFile)
            guides.forEach { name -> addFile(name, guide(name)) }
        }
    }

    suspend fun read(preset: EditorPreset, path: String, offset: Int) = withContext(Dispatchers.IO) {
        require(textExtension(path) && offset in 0..65_536)
        var source: String? = if (path in guides) guide(path).toString(Charsets.UTF_8) else null
        if (source == null) entries(preset) { name, bytes ->
            if (name == path) source = bytes.toString(Charsets.UTF_8)
        }
        val text = requireNotNull(source)
        require(offset <= text.length)
        val end = (offset + 8000).coerceAtMost(text.length)
        buildJsonObject { put("path", path); put("text", text.substring(offset, end))
            put("nextOffset", if (end < text.length) JsonPrimitive(end) else JsonNull) }
    }

    suspend fun stage(preset: EditorPreset, id: String, name: String, author: String): String =
        withContext(Dispatchers.IO) {
            require(validEditorPackageName(id) && name.length in 1..64 && author.length <= 120)
            val path = "imports/presets/${UUID.randomUUID()}"
            val storage = manager.paths.storage(preset.owner)
            val root = storage.directory(path)
            try {
                entries(preset) { entry, bytes ->
                    val output = storage.file("$path/$entry")
                    output.parentFile?.mkdirs()
                    when {
                        entry == preset.manifest -> {
                            val original = PluginJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                            output.writeText(JsonObject(original + mapOf(
                                "id" to JsonPrimitive(id), "name" to JsonPrimitive(name), "author" to JsonPrimitive(author)
                            )).toString(), Charsets.UTF_8)
                        }
                        entry.endsWith(".java") || entry.endsWith(".lua") -> output.writeText(
                            bytes.toString(Charsets.UTF_8).replace(preset.exampleId, id), Charsets.UTF_8)
                        else -> output.writeBytes(bytes)
                    }
                }
                path
            } catch (error: Exception) { root.deleteRecursively(); throw error }
        }

    fun byId(id: String) = requireNotNull(items.firstOrNull { it.id == id })

    private fun entries(preset: EditorPreset, entry: (String, ByteArray) -> Unit) {
        val data = application.assets.open("editor-presets/${preset.archive}").use { it.readBytes() }
        require(MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) } == preset.sha256)
        ZipInputStream(data.inputStream()).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                require(!item.isDirectory && item.name.split('/').none { it in setOf("", "..", ".") })
                entry(item.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }

    private fun textExtension(path: String) = path.substringAfterLast('.') in setOf("lua", "java", "kt", "json", "html", "css", "js", "md")
    private val guides = listOf("guides/component-catalog.md", "guides/quick-text-web.md", "guides/contracts.md")
    private fun guide(path: String) = application.assets.open("editor-presets/$path").use { it.readBytes() }
}
