package com.kigtts.next.media

import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Lossless, bounded original payload: JPEG APP15 segments or a HEIF UUID box. */
internal object EmbeddedPhotoOriginal {
    private val marker = "KIGTTS-ORIG1\u0000".toByteArray(Charsets.US_ASCII)
    private val uuid = byteArrayOf(0x4b, 0x49, 0x47, 0x54, 0x54, 0x53, 0x41, 0x52, -114, 0x61, 0x44, 0x52, 0x49, 0x47, 0x30, 0x31)
    private const val MAX_ORIGINAL = 32 * 1024 * 1024L
    private const val MAX_RECIPE = 16 * 1024
    private const val CHUNK = 60 * 1024
    internal data class Info(val recipe: String)

    fun write(image: File, original: File, recipe: String, output: File): File {
        val metadata = recipe.toByteArray(Charsets.UTF_8)
        require(metadata.size <= MAX_RECIPE && original.length() in 1..MAX_ORIGINAL)
        require(output.canonicalFile != image.canonicalFile && output.canonicalFile != original.canonicalFile)
        val length = original.length()
        val checksum = MessageDigest.getInstance("SHA-256")
        val header = java.io.ByteArrayOutputStream().also { raw ->
            DataOutputStream(raw).use { out -> out.writeInt(metadata.size); out.writeLong(length); out.write(ByteArray(32)); out.write(metadata) }
        }.toByteArray()
        try {
            var checksumPosition = -1L
            RandomAccessFile(image, "r").use { source ->
                val jpeg = source.readUnsignedShort() == 0xffd8
                val insertion = if (jpeg) jpegPrefixEnd(source) else source.length()
                val openEndedBox = if (jpeg) null else heifOpenEndedBox(source)
                checksumPosition = insertion + if (jpeg) 4 + marker.size + 2 + 12 else 24 + 12
                DataOutputStream(output.outputStream().buffered()).use { target ->
                    source.seek(0)
                    if (openEndedBox == null) copy(source, target, insertion) else {
                        copy(source, target, openEndedBox)
                        val size = insertion - openEndedBox
                        require(size <= 0xffffffffL)
                        target.writeInt(size.toInt())
                        source.skipBytes(4)
                        copy(source, target, size - 4)
                    }
                    java.security.DigestInputStream(original.inputStream().buffered(), checksum).use { raw ->
                        if (jpeg) {
                            val buffer = ByteArray(CHUNK)
                            var remaining = length
                            var sequence = 0
                            while (remaining > 0) {
                                val extra = if (sequence == 0) header.size else 0
                                val n = minOf(remaining, buffer.size.toLong(), (65533 - marker.size - 2 - extra).toLong()).toInt()
                                var read = 0
                                while (read < n) { val count = raw.read(buffer, read, n - read); check(count > 0); read += count }
                                target.writeShort(0xffef)
                                target.writeShort(2 + marker.size + 2 + extra + n)
                                target.write(marker); target.writeShort(sequence++)
                                if (extra > 0) target.write(header)
                                target.write(buffer, 0, n); remaining -= n
                            }
                        } else {
                            target.writeInt((24 + header.size + length).toInt())
                            target.writeInt(0x75756964); target.write(uuid); target.write(header)
                            require(raw.copyTo(target, CHUNK) == length)
                        }
                    }
                    copy(source, target, source.length() - insertion)
                }
            }
            RandomAccessFile(output, "rw").use { file ->
                file.seek(checksumPosition)
                file.write(checksum.digest())
            }
            return output
        } catch (failure: Throwable) { output.delete(); throw failure }
    }

    /** On invalid or truncated data no extracted copy is retained. */
    fun extract(image: File, output: File): Info? {
        require(image.canonicalFile != output.canonicalFile && !output.exists())
        var valid = false
        try {
            return RandomAccessFile(image, "r").use { source ->
                val jpeg = source.readUnsignedShort() == 0xffd8
                val segments = if (jpeg) jpegPayloads(source) else heifPayloads(source)
                val blocks = segments.iterator()
                if (!blocks.hasNext()) return@use null
                val first = blocks.next()
                source.seek(first.first)
                val metadataSize = source.readInt()
                val originalSize = source.readLong()
                require(metadataSize in 0..MAX_RECIPE && originalSize in 1..MAX_ORIGINAL && first.second > 44L + metadataSize)
                val checksum = ByteArray(32).also(source::readFully)
                val recipe = ByteArray(metadataSize).also(source::readFully).toString(Charsets.UTF_8)
                val digest = MessageDigest.getInstance("SHA-256")
                var remaining = originalSize
                java.security.DigestOutputStream(output.outputStream().buffered(), digest).use { sink ->
                    val start = source.filePointer
                    val firstBytes = first.second - (start - first.first)
                    require(firstBytes <= remaining)
                    copy(source, sink, firstBytes); remaining -= firstBytes
                    while (remaining > 0) {
                        require(blocks.hasNext())
                        val (offset, length) = blocks.next()
                        require(length in 1..remaining)
                        source.seek(offset); copy(source, sink, length); remaining -= length
                    }
                    require(!blocks.hasNext())
                }
                require(MessageDigest.isEqual(checksum, digest.digest()))
                valid = true
                Info(recipe)
            }
        } catch (_: Exception) { return null }
        finally { if (!valid) output.delete() }
    }

    private fun jpegPrefixEnd(file: RandomAccessFile): Long {
        file.seek(2)
        while (file.filePointer + 4 <= file.length()) {
            val at = file.filePointer
            val tag = file.readUnsignedShort()
            if (tag !in 0xffe0..0xffef && tag != 0xfffe) return at
            val size = file.readUnsignedShort()
            require(size >= 2 && at + 2 + size <= file.length())
            file.seek(at + 2 + size)
        }
        error("JPEG 缺少图片数据")
    }

    private fun jpegPayloads(file: RandomAccessFile): List<Pair<Long, Long>> {
        file.seek(2)
        val found = mutableListOf<Pair<Long, Long>>()
        while (file.filePointer + 4 <= file.length() && file.filePointer <= MAX_ORIGINAL + 8 * 1024 * 1024) {
            val tag = file.readUnsignedShort()
            if (tag == 0xffda || tag == 0xffd9 || tag !in 0xffc0..0xfffe) break
            val length = file.readUnsignedShort() - 2
            val start = file.filePointer
            require(length >= 0 && start + length <= file.length())
            if (tag == 0xffef && length > marker.size + 2 && ByteArray(marker.size).also(file::readFully).contentEquals(marker)) {
                require(found.size < 600 && file.readUnsignedShort() == found.size)
                val data = file.filePointer
                found += data to (start + length - data)
            }
            file.seek(start + length)
        }
        return found
    }

    private fun heifPayloads(file: RandomAccessFile): List<Pair<Long, Long>> {
        if (file.length() < 16) return emptyList()
        file.seek(4); if (file.readInt() != 0x66747970) return emptyList()
        file.seek(0)
        repeat(4096) {
            val start = file.filePointer
            if (start + 8 > file.length()) return emptyList()
            val size = file.readInt().toLong() and 0xffffffffL
            val tag = file.readInt()
            val fullSize = when (size) { 0L -> file.length() - start; 1L -> file.readLong(); else -> size }
            require(fullSize in (file.filePointer - start)..(file.length() - start))
            if (tag == 0x75756964 && fullSize >= (file.filePointer - start) + uuid.size &&
                ByteArray(uuid.size).also(file::readFully).contentEquals(uuid)) {
                return listOf(file.filePointer to (start + fullSize - file.filePointer))
            }
            file.seek(start + fullSize)
        }
        return emptyList()
    }

    private fun heifOpenEndedBox(file: RandomAccessFile): Long? {
        file.seek(4)
        require(file.readInt() == 0x66747970) { "仅支持 JPEG 和 HEIF 照片内嵌原图" }
        file.seek(0)
        repeat(4096) {
            val start = file.filePointer
            if (start == file.length()) return null
            require(start + 8 <= file.length())
            val size = file.readInt().toLong() and 0xffffffffL
            file.readInt()
            if (size == 0L) return start
            val length = if (size == 1L) file.readLong() else size
            require(length in (file.filePointer - start)..(file.length() - start))
            file.seek(start + length)
        }
        error("HEIF 容器结构过大")
    }

    private fun copy(source: RandomAccessFile, target: java.io.OutputStream, length: Long) {
        val bytes = ByteArray(minOf(CHUNK.toLong(), length).toInt())
        var remaining = length
        while (remaining > 0) {
            val count = minOf(remaining, bytes.size.toLong()).toInt()
            source.readFully(bytes, 0, count); target.write(bytes, 0, count); remaining -= count
        }
    }
}
