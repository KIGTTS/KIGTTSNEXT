package com.kigtts.next.legacy

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/** Private staging only. Unrecognized resources are never installed or executed. */
internal object LegacyArchive {
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_METADATA = 16L * 1024 * 1024
    fun read(source: File, staging: File): LegacyContent {
        val zipped = source.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!zipped) {
            require(source.length() <= MAX_METADATA) { "备份描述过大" }
            return LegacyContentDecoder.decode(legacyJson(source.readText(Charsets.UTF_8)), resource = { null })
        }
        staging.mkdirs()
        ZipFile(source).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.size <= 12000) { "备份文件数量过多" }
            val names = HashSet<String>()
            var total = 0L
            entries.forEach { entry ->
                val name = entry.name
                require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\') && !name.contains(':') && name.split('/').none { it == ".." || it == "." }) { "备份包含不安全路径" }
                require(names.add(name)) { "备份包含重复文件" }
                if (!entry.isDirectory && (name in setOf("manifest.json", "preset.json", "preferences.json") ||
                    name.startsWith("audio/") || name.startsWith("images/") || name.startsWith("resources/quick_cards/") || name.startsWith("resources/soundboard/"))) {
                    val target = File(staging, name).canonicalFile
                    require(target.toPath().startsWith(staging.canonicalFile.toPath())) { "备份包含越界路径" }
                    target.parentFile!!.mkdirs()
                    val limit = if (name.endsWith(".json")) MAX_METADATA else MAX_BYTES - total
                    zip.getInputStream(entry).use { input -> target.outputStream().use { output -> total += copy(input, output, minOf(limit, MAX_BYTES - total)) } }
                }
            }
        }
        val manifest = File(staging, "manifest.json").takeIf { it.isFile } ?: File(staging, "preset.json")
        require(manifest.isFile) { "备份缺少描述文件" }
        val preferences = File(staging, "preferences.json").takeIf { it.isFile }?.readText(Charsets.UTF_8)?.let(::legacyJson)
        return LegacyContentDecoder.decode(legacyJson(manifest.readText(Charsets.UTF_8)), preferences) { path ->
            if (path.startsWith("/") || path.startsWith("file:")) {
                // Old full backups store the former absolute private path. Rebase only known roots.
                val normalized = path.removePrefix("file://")
                val mapped = when {
                    "/files/quick_cards/" in normalized -> "resources/quick_cards/" + normalized.substringAfter("/files/quick_cards/")
                    "/files/soundboard/audio/" in normalized -> "resources/soundboard/" + normalized.substringAfter("/files/soundboard/audio/")
                    else -> return@decode null
                }
                resource(staging, mapped)
            } else resource(staging, path)
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
