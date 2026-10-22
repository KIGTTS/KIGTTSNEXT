package com.kigtts.next.audio

import org.json.JSONObject
import java.io.File
import java.nio.file.Files

/** Read the original installation in place; never move or open its large model files. */
internal object LegacyPiperPacks {
    fun exists(files: File, pluginData: File): Boolean = listOf(
        File(files, "models/voice"), File(pluginData, "engine.piper/models/voices")
    ).any { root ->
        if (!root.isDirectory || Files.isSymbolicLink(root.toPath())) false
        else Files.newDirectoryStream(root.toPath()).use { paths ->
            paths.asSequence().take(1024).any { path ->
                val pack = path.toFile()
                pack.isDirectory && !pack.name.startsWith('.') && !Files.isSymbolicLink(path) && valid(pack)
            }
        }
    }
    private fun valid(pack: File) = runCatching {
        fun file(name: String): File? {
            if (name.isBlank() || name.contains('\\') || File(name).isAbsolute || name.split('/').any { it == ".." }) return null
            return File(pack, name).canonicalFile.takeIf { it.toPath().startsWith(pack.canonicalFile.toPath()) && it.isFile }
        }
        val manifest = file("manifest.json") ?: return@runCatching false
        val bytes = manifest.inputStream().use { it.readNBytes(65537) }
        if (bytes.size > 65536) return@runCatching false
        val paths = JSONObject(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")).optJSONObject("files") ?: return@runCatching false
        val model = file(paths.optString("model")) ?: return@runCatching false
        val config = file(paths.optString("config")) ?: return@runCatching false
        model.extension.equals("onnx", true) && model.length() > 0 && config.length() > 0
    }.getOrDefault(false)
}
