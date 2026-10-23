package com.kigtts.next.ui.plugins

import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.runtime.PluginPermissions
import kotlinx.serialization.json.*
import java.util.zip.ZipFile

internal data class PackageInstallerInfo(val candidate: PendingPackage, val description: String = "",
    val author: String = "", val symbol: String? = null, val licenseText: String = "", val image: android.graphics.Bitmap? = null,
    val previous: com.kigtts.plugin.runtime.PackageManifest? = null) {
    val manifest get() = candidate.preview.manifest
    val permissions get() = manifest.capabilities.filter { it in PluginPermissions.sensitive || it in devicePermissions }.distinct()
    val addedPermissions get() = permissions.filterNot { it in previous?.capabilities.orEmpty() }
    val existingPermissions get() = permissions.filter { it in previous?.capabilities.orEmpty() }
    companion object { private val devicePermissions = setOf("camera", "microphone", "downloads", "motion-sensors") }
}

/** Read bounded, signed presentation assets only; never run a package to show its installer. */
internal fun installerInfo(candidate: PendingPackage, previous: com.kigtts.plugin.runtime.PackageManifest? = null): PackageInstallerInfo = ZipFile(candidate.file).use { zip ->
    fun readBytes(path: String, limit: Int): ByteArray? {
        if (path !in candidate.preview.manifest.files) return null
        val entry = zip.getEntry(path) ?: return null
        if (entry.size !in 0..limit.toLong()) return null
        return zip.getInputStream(entry).use { input ->
            val bytes = ByteArray(limit + 1)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            if (size <= limit) bytes.copyOf(size) else null
        }
    }
    fun read(path: String, limit: Int) = readBytes(path, limit)?.toString(Charsets.UTF_8).orEmpty()
    val metadata = runCatching { PluginJson.parseToJsonElement(read("assets/plugin-info.json", 16_384)).jsonObject }.getOrNull()
    fun field(key: String, limit: Int) = (metadata?.get(key) as? JsonPrimitive)?.contentOrNull?.take(limit).orEmpty()
    val licensePaths = candidate.preview.manifest.files.keys.filter { path ->
        path in setOf("LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "NOTICE.md") ||
            (path.startsWith("assets/licenses/") || path.startsWith("licenses/")) &&
            (path.endsWith(".txt", true) || path.endsWith(".md", true) || path.substringAfterLast('/') == "LICENSE")
    }.sorted().take(16)
    var remaining = 262_144
    val license = licensePaths.mapNotNull { path ->
        read(path, minOf(131_072, remaining)).takeIf(String::isNotBlank)?.let { content ->
            remaining -= content.toByteArray(Charsets.UTF_8).size
            "$path\n\n$content"
        }
    }.joinToString("\n\n────────\n\n")
    PackageInstallerInfo(candidate, field("description", 8000), field("author", 120),
        candidate.preview.manifest.icon?.symbol ?: field("symbol", 80).takeIf { it.matches(Regex("[a-z][a-z0-9_]{0,79}")) }, license,
        candidate.preview.manifest.icon?.path?.let { path -> readBytes(path, 1_048_576)?.let { packageIconBitmap(path, it) } }, previous)
}
