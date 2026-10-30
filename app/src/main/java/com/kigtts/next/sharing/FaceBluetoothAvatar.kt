package com.kigtts.next.sharing

import android.graphics.*
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A cached, bounded GATT value makes the avatar independent of the HTTP connection. */
internal fun faceBluetoothAvatar(data: String?): ByteArray? = runCatching {
    val bytes = data?.takeIf { it.length < 65536 }?.let { Base64.decode(it, Base64.DEFAULT) } ?: return@runCatching null
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
    try {
        for (size in listOf(32, 24, 16)) {
            // Convert to opaque sRGB before omitting the redundant ICC profile (560 bytes on EMUI).
            val thumbnail = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
            try {
                Canvas(thumbnail).drawBitmap(source, null, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
                for (quality in listOf(50, 25)) {
                    val encoded = ByteArrayOutputStream().use { output ->
                        @Suppress("DEPRECATION") val format = Bitmap.CompressFormat.WEBP
                        check(thumbnail.compress(format, quality, output)); simpleWebp(output.toByteArray())
                    }
                    if (encoded.size in 1..480) return@runCatching encoded
                }
            } finally { thumbnail.recycle() }
        }
        null
    } finally { source.recycle() }
}.getOrNull()

/** Our own opaque, single-frame encoder output can use the simple WebP container. */
private fun simpleWebp(bytes: ByteArray): ByteArray {
    require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP")
    var offset = 12
    while (offset + 8 <= bytes.size) {
        val length = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(length >= 0 && length <= bytes.size - offset - 8)
        val end = offset + 8 + length + length % 2
        require(end <= bytes.size)
        if (String(bytes, offset, 4, Charsets.US_ASCII) in setOf("VP8 ", "VP8L")) {
            return ByteArray(12 + end - offset).also { result ->
                bytes.copyInto(result, 0, 0, 12)
                ByteBuffer.wrap(result, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(result.size - 8)
                bytes.copyInto(result, 12, offset, end)
            }
        }
        offset = end
    }
    error("Missing WebP image")
}
