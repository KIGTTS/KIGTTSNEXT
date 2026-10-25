package com.kigtts.next.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** Private durable copies: document picker grants can disappear after restart. */
class AppearanceAssets(private val context: Context) {
    private val root = File(context.filesDir, "appearance-assets").apply { mkdirs() }
    fun file(key: String?): File? = key?.takeIf { it.matches(Regex("[a-f0-9]{64}\\.(ttf|otf|jpg)")) }
        ?.let { File(root, it) }?.takeIf { it.isFile }

    fun importFont(uri: Uri): String = requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
        val bytes = readBounded(stream, MAX_FONT)
        require(bytes.size in 12..MAX_FONT) { "请选择小于 40 MB 的 TTF 或 OTF 字体" }
        val header = bytes.take(4).toByteArray()
        require(header.contentEquals(byteArrayOf(0, 1, 0, 0)) || String(header, Charsets.US_ASCII) == "OTTO") { "请选择 TTF 或 OTF 字体" }
        val key = digest(bytes) + if (String(header, Charsets.US_ASCII) == "OTTO") ".otf" else ".ttf"
        val target = File(root, key)
        if (!target.exists()) target.writeBytes(bytes)
        try { Typeface.createFromFile(target); key } catch (e: Exception) { target.delete(); throw IllegalArgumentException("无法读取这个字体", e) }
    }

    fun importBackground(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        requireNotNull(context.contentResolver.openInputStream(uri)).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..40000 && bounds.outHeight in 1..40000) { "无法读取这张图片" }
        val options = BitmapFactory.Options().apply {
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 2560) inSampleSize = inSampleSize.coerceAtLeast(1) * 2
        }
        var bitmap = requireNotNull(context.contentResolver.openInputStream(uri)).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("无法读取这张图片")
        val orientation = runCatching { requireNotNull(context.contentResolver.openInputStream(uri)).use {
            androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
        } }.getOrDefault(1)
        val matrix = android.graphics.Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            }
        }
        if (!matrix.isIdentity) {
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (upright !== bitmap) bitmap.recycle()
            bitmap = upright
        }
        val bytes = try { java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray() }
            finally { bitmap.recycle() }
        val key = digest(bytes) + ".jpg"
        File(root, key).takeUnless { it.exists() }?.writeBytes(bytes)
        return key
    }

    companion object {
        const val MAX_FONT = 40 * 1024 * 1024
        fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= limit) { "文件过大" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun heightScale(typeface: Typeface): Float {
            fun ink(face: Typeface): Int = Rect().also { rect ->
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 1000f; this.typeface = face }.getTextBounds("国Ag", 0, 3, rect)
            }.height().coerceAtLeast(1)
            return (ink(Typeface.DEFAULT).toFloat() / ink(typeface)).coerceIn(.65f, 1.6f)
        }
    }
}
