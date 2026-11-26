package com.kigtts.next.ui.plugins

import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.runtime.PackageManifest
import org.json.JSONObject
import java.io.File

internal data class InstalledPluginDetails(val name: String, val version: String, val description: String,
    val author: String = "", val license: String = "", val licenseText: String = "", val homepage: String = "")

/** Read only bounded presentation files listed in the package's signed manifest. */
internal fun installedPresentation(directory: File, manifest: PackageManifest): InstalledPluginDetails {
    val root = directory.canonicalFile
    fun read(path: String, maximum: Int): String {
        if (path !in manifest.files) return ""
        val file = File(root, path).canonicalFile
        if (!file.toPath().startsWith(root.toPath()) || !file.isFile || file.length() !in 0..maximum.toLong()) return ""
        return file.inputStream().use { stream ->
            val bytes = ByteArray(maximum + 1); var length = 0
            while (length < bytes.size) { val count = stream.read(bytes, length, bytes.size - length); if (count < 0) break; length += count }
            if (length > maximum) "" else String(bytes, 0, length, Charsets.UTF_8)
        }
    }
    val json = runCatching { JSONObject(read("assets/plugin-info.json", 16_384)) }.getOrDefault(JSONObject())
    fun field(key: String, max: Int) = json.optString(key).take(max)
    val description = field("description", 8000).ifBlank {
        listOf("assets/about.md", "assets/README.md", "README.md").firstNotNullOfOrNull { read(it, 16_384).takeIf(String::isNotBlank) }.orEmpty()
    }
    var remaining = 262_144
    val licenses = manifest.files.keys.filter {
        it in setOf("LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "NOTICE.md") ||
            (it.startsWith("assets/licenses/") || it.startsWith("licenses/")) && it.substringAfterLast('.').lowercase() in setOf("txt", "md")
    }.sorted().take(16).mapNotNull { path -> read(path, minOf(131_072, remaining)).takeIf(String::isNotBlank)?.let {
        remaining -= it.toByteArray(Charsets.UTF_8).size; "$path\n\n$it"
    } }.joinToString("\n\n────────\n\n")
    return InstalledPluginDetails(manifest.name, manifest.version, description,
        manifest.author.orEmpty().ifBlank { field("author", 120) }, manifest.license, licenses, field("homepage", 2048))
}

internal fun installedPluginDetails(model: AppViewModel, id: String): InstalledPluginDetails {
    val status = model.pluginManager.status.firstOrNull { it.installed.manifest.id == id }
    val descriptor = model.registry.plugin(id)?.descriptor
    if (status != null) {
        val info = installedPresentation(model.pluginManager.packages.currentDirectory(id), status.installed.manifest)
        return info.copy(description = info.description.ifBlank { descriptor?.description.orEmpty() },
            author = if (status.installed.publisherSha256 == com.kigtts.next.plugins.control.STORE_PUBLISHER) "KIGTTS" else info.author)
    }
    val managed = model.registry.plugin(id.substringBefore("::"))?.contributions?.managedPackages?.firstOrNull { it.id == id.substringAfter("::", "") }
    return InstalledPluginDetails(managed?.name ?: descriptor?.name ?: id, managed?.version ?: descriptor?.version.orEmpty(),
        descriptor?.description.orEmpty(), license = descriptor?.license.orEmpty())
}
