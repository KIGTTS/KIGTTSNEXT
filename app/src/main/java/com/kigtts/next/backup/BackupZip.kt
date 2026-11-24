package com.kigtts.next.backup

import com.kigtts.next.legacy.LegacyArchive
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal fun ZipFile.backupEntries(limit: Long, maxEntries: Int = 12_000): Map<String, ZipEntry> {
    require(size() <= maxEntries) { "备份文件数量过多" }
    var expanded = 0L
    return buildMap {
        entries().asSequence().forEach { entry ->
            val path = entry.name.removeSuffix("/")
            require(path.length in 1..1024 && path.split('/').all {
                it.isNotBlank() && it !in setOf(".", "..") && it.none { ch -> ch == ':' || ch == '\\' || ch.isISOControl() }
            }) { "备份包含不安全路径" }
            require(put(path, entry) == null) { "备份包含重复文件" }
            require(entry.size in 0..(limit - expanded)) { "备份展开后过大" }
            expanded += entry.size
        }
        keys.forEach { path ->
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                require(get(parent)?.isDirectory != false) { "备份包含冲突路径" }
                parent = parent.substringBeforeLast('/', "")
            }
        }
    }
}

internal fun ZipFile.copyBackupEntry(entry: ZipEntry, output: OutputStream, maxBytes: Long) {
    require(!entry.isDirectory && entry.size in 0..maxBytes) { "备份资源过大" }
    CheckedInputStream(getInputStream(entry), CRC32()).use { input ->
        val count = LegacyArchive.copy(input, output, maxBytes)
        require(count == entry.size && input.checksum.value == entry.crc) { "备份文件已损坏" }
    }
}

internal fun ZipFile.readBackupText(entry: ZipEntry, maxBytes: Long = LegacyArchive.MAX_METADATA): String {
    val bytes = java.io.ByteArrayOutputStream()
    copyBackupEntry(entry, bytes, maxBytes)
    return bytes.toString("UTF-8")
}
