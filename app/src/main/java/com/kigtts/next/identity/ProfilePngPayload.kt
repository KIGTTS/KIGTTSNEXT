package com.kigtts.next.identity

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32

/** Private ancillary PNG chunk; ordinary viewers ignore it, image recompression may remove it. */
internal object ProfilePngPayload {
    private val signature = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private const val PAYLOAD = 0x6b694754 // kiGT
    private const val END = 0x49454e44
    private val type = byteArrayOf(107, 105, 71, 84)

    fun embed(png: File, archive: File, output: File) {
        require(archive.length() in 1..ProfileArchive.MAX_BYTES)
        DataInputStream(png.inputStream().buffered()).use { input ->
            DataOutputStream(output.outputStream().buffered()).use { out ->
                check(input.readSignature())
                out.write(signature)
                while (true) {
                    val count = input.readInt()
                    val chunk = input.readInt()
                    check(count in 0..png.length() && chunk != PAYLOAD)
                    if (chunk == END) {
                        out.writeInt(archive.length().toInt()); out.writeInt(PAYLOAD)
                        val crc = CRC32().apply { update(type) }
                        DataInputStream(archive.inputStream().buffered()).use { it.transfer(out, archive.length().toInt(), crc) }
                        out.writeInt(crc.value.toInt())
                    }
                    out.writeInt(count); out.writeInt(chunk)
                    input.transfer(out, count)
                    out.writeInt(input.readInt())
                    if (chunk == END) break
                }
            }
        }
    }

    fun extract(png: File, output: File): Boolean = runCatching {
        DataInputStream(png.inputStream().buffered()).use { input ->
            if (!input.readSignature()) return@use false
            repeat(65_536) {
                val count = input.readInt()
                val chunk = input.readInt()
                if (count !in 0..png.length() || chunk == END) return@use false
                if (chunk == PAYLOAD) {
                    if (count.toLong() !in 1..ProfileArchive.MAX_BYTES) return@use false
                    val crc = CRC32().apply { update(type) }
                    output.outputStream().buffered().use { input.transfer(it, count, crc) }
                    return@use crc.value.toInt() == input.readInt()
                }
                input.skipFully(count.toLong() + 4)
            }
            false
        }
    }.getOrDefault(false).also { if (!it) output.delete() }

    private fun DataInputStream.readSignature(): Boolean = ByteArray(signature.size).also { readFully(it) }.contentEquals(signature)
    private fun DataInputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val count = skip(remaining)
            if (count > 0L) remaining -= count else { readByte(); remaining-- }
        }
    }
    private fun DataInputStream.transfer(out: OutputStream, count: Int, crc: CRC32? = null) {
        val buffer = ByteArray(minOf(count, 32 * 1024))
        var remaining = count
        while (remaining > 0) {
            val n = minOf(remaining, buffer.size)
            readFully(buffer, 0, n)
            out.write(buffer, 0, n); crc?.update(buffer, 0, n)
            remaining -= n
        }
    }
}
