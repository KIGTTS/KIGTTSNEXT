package com.kigtts.next.state

import java.io.File
import java.util.zip.ZipFile
import com.kigtts.next.legacy.legacyJson

internal enum class ExternalFileType(val engineId: String? = null, val engineName: String? = null) {
    QUICK_TEXT_BACKUP, LEGACY_BACKUP, PROFILE_ARCHIVE,
    PIPER_VOICE("engine.piper", "Piper 语音引擎"),
    SHARED_CLONE_VOICE("engine.zipvoice-clone", "ZipVoice 语音克隆引擎"),
    CLONE_VOICE("engine.zipvoice-clone", "ZipVoice 语音克隆引擎"),
    QWEN_VOICE("engine.qwen3-clone", "千问语音克隆引擎"),
    PLUGIN, EXPRESSION, UNKNOWN;

    val isVoice get() = engineId != null
}

/** Routing only: the destination validates the complete archive before importing. */
internal object ExternalFileTypes {
    fun detect(displayName: String?): ExternalFileType = when (displayName.orEmpty().substringAfterLast('.', "").lowercase()) {
        "kigcards" -> ExternalFileType.QUICK_TEXT_BACKUP
        "kigtpk", "kigspk", "kigcard", "kigconfig" -> ExternalFileType.LEGACY_BACKUP
        "kigvpk" -> ExternalFileType.PIPER_VOICE
        "kigexpression" -> ExternalFileType.EXPRESSION
        "kigplugin" -> ExternalFileType.PLUGIN
        "mhtml", "mht" -> ExternalFileType.PROFILE_ARCHIVE
        else -> ExternalFileType.UNKNOWN
    }

    fun inspect(file: File, displayName: String): ExternalFileType {
        val declared = detect(displayName)
        if (declared == ExternalFileType.PROFILE_ARCHIVE || declared == ExternalFileType.UNKNOWN) {
            if (com.kigtts.next.identity.ProfileArchive.recognizes(file)) return ExternalFileType.PROFILE_ARCHIVE
            if (declared == ExternalFileType.PROFILE_ARCHIVE) return ExternalFileType.UNKNOWN
        }
        if (!displayName.endsWith(".kigcard", true) && !declared.isVoice && declared != ExternalFileType.UNKNOWN) return declared
        val zipped = file.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!zipped) return if (file.length() <= MAX_METADATA) legacy(file.readText(Charsets.UTF_8)) else ExternalFileType.UNKNOWN
        return ZipFile(file).use { zip ->
            require(zip.size() <= 12000) { "文件数量过多" }
            val names = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
            when {
                "plugin.json" in names -> ExternalFileType.PLUGIN
                "cards.json" in names -> ExternalFileType.QUICK_TEXT_BACKUP
                "voice.json" in names && "reference.wav" in names -> {
                    val metadata = json(zip, "voice.json", 16 * 1024L)
                    if (metadata == null || metadata.optString("name").isBlank() || metadata.optString("referenceText").isBlank())
                        ExternalFileType.UNKNOWN
                    else when (metadata.optString("engine").lowercase()) {
                        "" -> ExternalFileType.SHARED_CLONE_VOICE
                        "zipvoice", "engine.zipvoice-clone" -> ExternalFileType.CLONE_VOICE
                        "qwen3", "qwen3-tts", "engine.qwen3-clone" -> ExternalFileType.QWEN_VOICE
                        else -> ExternalFileType.UNKNOWN
                    }
                }
                names.any { it.endsWith(".onnx", true) && "$it.json" in names } -> ExternalFileType.PIPER_VOICE
                else -> {
                    val manifests = names.asSequence().filter { it.substringAfterLast('/') == "manifest.json" }.take(16)
                    if (manifests.any { manifest -> isPiperManifest(zip, names, manifest) }) ExternalFileType.PIPER_VOICE
                    else (json(zip, "manifest.json") ?: json(zip, "preset.json"))?.let { legacy(it.toString()) }
                        ?: ExternalFileType.UNKNOWN
                }
            }
        }
    }

    private fun isPiperManifest(zip: ZipFile, names: Set<String>, manifest: String): Boolean {
        val files = json(zip, manifest)?.optJSONObject("files") ?: return false
        val parent = manifest.removeSuffix("manifest.json")
        val model = files.optString("model").replace('\\', '/')
        val config = files.optString("config").replace('\\', '/').ifBlank { "$model.json" }
        return model.endsWith(".onnx", true) && parent + model in names && parent + config in names
    }

    private fun json(zip: ZipFile, name: String, limit: Long = MAX_METADATA): org.json.JSONObject? {
        val entry = zip.getEntry(name)?.takeIf { !it.isDirectory && it.size in 1..limit } ?: return null
        return runCatching { zip.getInputStream(entry).use { stream ->
            val output = java.io.ByteArrayOutputStream()
            com.kigtts.next.legacy.LegacyArchive.copy(stream, output, limit)
            legacyJson(output.toString("UTF-8"))
        } }.getOrNull()
    }

    private fun legacy(text: String): ExternalFileType = runCatching {
        if (legacyJson(text).optString("type") in setOf("quick_subtitle", "soundboard", "kigtts_quick_cards", "kigtts_config_backup",
                com.kigtts.next.backup.CONFIG_BACKUP_TYPE))
            ExternalFileType.LEGACY_BACKUP else ExternalFileType.UNKNOWN
    }.getOrDefault(ExternalFileType.UNKNOWN)
    private const val MAX_METADATA = 1024L * 1024
}
