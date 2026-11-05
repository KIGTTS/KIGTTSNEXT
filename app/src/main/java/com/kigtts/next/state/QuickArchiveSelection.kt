package com.kigtts.next.state

import com.kigtts.plugin.api.*
import com.kigtts.next.plugins.quicktext.QuickCardsArchive
import java.io.File
import java.util.zip.*

/** Reads bounded metadata before showing the group picker; extraction still uses the normal importer. */
internal object QuickArchiveSelection {
    fun read(source: File): QuickCardsBundle = ZipFile(source).use { zip ->
        require(source.length() <= QuickCardsArchive.MAX_BYTES && zip.size() <= 2049) { "备份文件过大" }
        val entries = zip.entries().toList()
        require(entries.map { it.name }.distinct().size == entries.size &&
            entries.all { QuickCardsArchive.safe(it.name.removeSuffix("/")) }) { "备份文件路径不合法" }
        val entry = requireNotNull(zip.getEntry("cards.json")) { "缺少快捷文本内容" }
        val data = zip.getInputStream(entry).use { input ->
            java.io.ByteArrayOutputStream().also { output ->
                val buffer = ByteArray(8192)
                while (output.size() <= QuickCardsArchive.MAX_JSON) {
                    val n = input.read(buffer, 0, minOf(buffer.size, QuickCardsArchive.MAX_JSON + 1 - output.size()))
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
            }.toByteArray()
        }
        require(data.size <= QuickCardsArchive.MAX_JSON) { "快捷文本数据过大" }
        PluginJson.decodeFromString<QuickCardsBundle>(data.toString(Charsets.UTF_8)).also {
            require(it.schema == 1 && it.groups.size <= 256 && it.entries.size <= 10_000 && it.media.size <= 2048) { "备份格式或数量不合法" }
            require(it.groups.map { it.id }.distinct().size == it.groups.size && it.entries.map { it.id }.distinct().size == it.entries.size) { "备份包含重复标识" }
        }
    }

    fun write(source: File, destination: File, selected: Set<String>) {
        val all = read(source)
        require(selected.isNotEmpty() && selected.all { id -> all.groups.any { it.id == id } }) { "请选择要还原的分组" }
        val entries = all.entries.filter { it.groupId in selected }
        val mediaIds = entries.mapNotNull { it.mediaId }.toSet()
        val media = all.media.filter { it.id in mediaIds }
        val bundle = all.copy(groups = all.groups.filter { it.id in selected }, entries = entries, media = media)
        val files = media.flatMap { listOfNotNull(it.primary.takeUnless { _ -> it.kind == "WEB" }, it.video) }.toSet()
        val roots = media.mapNotNull { it.root }.map { it.trimEnd('/') + "/" }
        var total = 0L
        destination.parentFile!!.mkdirs()
        try {
            ZipFile(source).use { zip -> ZipOutputStream(destination.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("cards.json"))
                output.write(PluginJson.encodeToString(bundle).toByteArray(Charsets.UTF_8)); output.closeEntry()
                zip.entries().asSequence().filter { !it.isDirectory && (it.name in files || roots.any { root -> it.name.startsWith(root) }) }.forEach { entry ->
                    require(QuickCardsArchive.safe(entry.name)) { "附件路径不合法" }
                    output.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            total += n; require(total <= QuickCardsArchive.MAX_BYTES) { "附件展开后过大" }
                            output.write(buffer, 0, n)
                        }
                    }
                    output.closeEntry()
                }
            } }
        } catch (error: Exception) { destination.delete(); throw error }
    }
}
