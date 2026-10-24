package com.kigtts.next.state

import java.io.File
import java.util.zip.ZipFile
import com.kigtts.next.legacy.legacyJson

internal enum class ExternalFileType { QUICK_TEXT_BACKUP, LEGACY_BACKUP, PIPER_VOICE, PLUGIN, UNKNOWN }

/** Routing only: the destination validates the complete archive before importing. */
internal object ExternalFileTypes {
    fun detect(displayName: String?): ExternalFileType = when (displayName.orEmpty().substringAfterLast('.', "").lowercase()) {
        "kigcards" -> ExternalFileType.QUICK_TEXT_BACKUP
        "kigtpk", "kigspk", "kigcard", "kigconfig" -> ExternalFileType.LEGACY_BACKUP
        "kigvpk" -> ExternalFileType.PIPER_VOICE
        "kigplugin" -> ExternalFileType.PLUGIN
        else -> ExternalFileType.UNKNOWN
    }

    fun inspect(file: File, displayName: String): ExternalFileType {
        detect(displayName).takeUnless { it == ExternalFileType.UNKNOWN }?.let { return it }
        val zipped = file.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!zipped) return if (file.length() <= MAX_METADATA) legacy(file.readText(Charsets.UTF_8)) else ExternalFileType.UNKNOWN
        return ZipFile(file).use { zip ->
            require(zip.size() <= 12000) { "文件数量过多" }
            val names = zip.entries().asSequence().map { it.name }.toSet()
            when {
                "plugin.json" in names -> ExternalFileType.PLUGIN
                "cards.json" in names -> ExternalFileType.QUICK_TEXT_BACKUP
                names.any { it.endsWith(".onnx") && "$it.json" in names } -> ExternalFileType.PIPER_VOICE
                else -> {
                    val entry = zip.getEntry("manifest.json") ?: zip.getEntry("preset.json")
                    if (entry == null || entry.size !in 1..MAX_METADATA) ExternalFileType.UNKNOWN
                    else zip.getInputStream(entry).use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        com.kigtts.next.legacy.LegacyArchive.copy(stream, output, MAX_METADATA)
                        val json = legacyJson(output.toString("UTF-8"))
                        val files = json.optJSONObject("files")
                        if (files != null && files.keys().asSequence().any { key -> files.optString(key).endsWith(".onnx") }) ExternalFileType.PIPER_VOICE
                        else legacy(json.toString())
                    }
                }
            }
        }
    }

    private fun legacy(text: String): ExternalFileType = runCatching {
        if (legacyJson(text).optString("type") in setOf("quick_subtitle", "soundboard", "kigtts_quick_cards", "kigtts_config_backup"))
            ExternalFileType.LEGACY_BACKUP else ExternalFileType.UNKNOWN
    }.getOrDefault(ExternalFileType.UNKNOWN)
    private const val MAX_METADATA = 1024L * 1024
}
