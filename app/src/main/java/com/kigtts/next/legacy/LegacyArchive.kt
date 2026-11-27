package com.kigtts.next.legacy

import com.kigtts.next.backup.backupEntries
import com.kigtts.next.backup.copyBackupEntry
import com.kigtts.next.backup.readBackupText
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/** Private staging only. Unrecognized resources are never installed or executed. */
internal object LegacyArchive {
    const val MAX_BYTES = 32L * 1024 * 1024 * 1024
    const val MAX_METADATA = 16L * 1024 * 1024
    private val resourceRoots = setOf("quick_cards", "soundboard", "font", "voice", "lock_screen")

    fun read(source: File, staging: File): LegacyContent {
        require(source.length() in 1..MAX_BYTES)
        val zipped = source.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!zipped) {
            require(source.length() <= MAX_METADATA) { "备份描述过大" }
            return LegacyContentDecoder.decode(legacyJson(source.readText(Charsets.UTF_8)), resource = { null })
        }
        staging.mkdirs()
        return ZipFile(source).use { zip ->
            val entries = zip.backupEntries(MAX_BYTES)
            val descriptor = entries["manifest.json"] ?: requireNotNull(entries["preset.json"]) { "备份缺少描述文件" }
            val manifest = legacyJson(zip.readBackupText(descriptor))
            val preferences = entries["preferences.json"]?.let { legacyJson(zip.readBackupText(it)) }
            val full = manifest.optString("type") == "kigtts_config_backup"
            entries.forEach { (name, entry) ->
                val folder = name.substringBefore('/')
                val resourceType = name.removePrefix("resources/").substringBefore('/')
                if (!entry.isDirectory && (folder in setOf("audio", "images") ||
                    full && folder == "resources" && resourceType in resourceRoots)) {
                    val target = File(staging, name).canonicalFile
                    require(target.toPath().startsWith(staging.canonicalFile.toPath())) { "备份包含越界路径" }
                    target.parentFile!!.mkdirs()
                    val limit = if (name.endsWith(".json", true)) MAX_METADATA else MAX_BYTES
                    target.outputStream().use { zip.copyBackupEntry(entry, it, limit) }
                }
            }
            LegacyContentDecoder.decode(manifest, preferences) { path -> resource(staging, rebase(path)) }
                .copy(resourceRoot = File(staging, "resources").takeIf { full && it.isDirectory })
        }
    }

    private fun rebase(path: String): String {
        val relative = path.removePrefix("file://")
        return when {
            "/files/quick_cards/" in relative -> "resources/quick_cards/" + relative.substringAfter("/files/quick_cards/")
            "/files/soundboard/audio/" in relative -> "resources/soundboard/" + relative.substringAfter("/files/soundboard/audio/")
            relative.startsWith("quick_cards/") -> "resources/$relative"
            relative.startsWith("soundboard/audio/") -> "resources/soundboard/" + relative.substringAfter("soundboard/audio/")
            else -> relative
        }
    }
    private fun resource(root: File, name: String): File? {
        if (name.isBlank() || name.contains('\\') || name.contains(':') || name.startsWith('/') || name.split('/').any { it in setOf("", ".", "..") }) return null
        return File(root, name).canonicalFile.takeIf { it.toPath().startsWith(root.canonicalFile.toPath()) && it.isFile }
    }
    fun copy(input: InputStream, output: OutputStream, limit: Long): Long {
        var total = 0L; val buffer = ByteArray(65536)
        while (true) { val size = input.read(buffer); if (size < 0) break
            total += size; require(total <= limit) { "备份过大，请分批导入" }; output.write(buffer, 0, size) }
        return total
    }
}
