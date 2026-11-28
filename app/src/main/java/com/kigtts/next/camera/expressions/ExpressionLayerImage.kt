package com.kigtts.next.camera.expressions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import com.kigtts.next.media.MediaBitmapDecoder
import com.kigtts.next.media.UiIcons
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt

internal const val EXPRESSION_LAYER_EDGE = 320
private const val MAX_PHOTO_BYTES = 32 * 1024 * 1024
internal const val EMPTY_EXPRESSION_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 320"/>"""

internal fun expressionPhotoLayer(context: Context, uri: Uri): ExpressionLayer {
    val source = File.createTempFile("expression-photo-", ".image", context.cacheDir)
    return try {
        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            source.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = MAX_PHOTO_BYTES
                while (true) {
                    val bytes = input.read(buffer)
                    if (bytes == -1) break
                    require(bytes <= remaining)
                    output.write(buffer, 0, bytes)
                    remaining -= bytes
                }
            }
        }
        ExpressionLayer(EMPTY_EXPRESSION_SVG, bitmapWebp = expressionStickerImage(source))
    } finally {
        source.delete()
    }
}

/** Size the sticker once and embed the encoded copy so a pack can be shared independently. */
internal fun expressionStickerImage(file: File): String {
    val bitmap = if (file.extension.equals("svg", true)) {
        require(file.length() in 1..ExpressionPack.MAX_SVG.toLong())
        UiIcons.renderSvg(file.readText(), EXPRESSION_LAYER_EDGE)
    } else requireNotNull(MediaBitmapDecoder.decode(file, EXPRESSION_LAYER_EDGE))
    return try {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= EXPRESSION_LAYER_EDGE) encodeExpressionBitmap(bitmap)
        else {
            val ratio = EXPRESSION_LAYER_EDGE.toFloat() / largest
            val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
            try { encodeExpressionBitmap(scaled) }
            finally { scaled.recycle() }
        }
    } finally {
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun encodeExpressionBitmap(bitmap: Bitmap): String = ByteArrayOutputStream().use { output ->
    for (quality in listOf(90, 76, 60, 42)) {
        output.reset()
        check(bitmap.compress(Bitmap.CompressFormat.WEBP, quality, output))
        if ((output.size() + 2) / 3 * 4 <= ExpressionPack.MAX_SVG) {
            return@use Base64.getEncoder().encodeToString(output.toByteArray())
        }
    }
    error("Sticker artwork exceeds the pack size limit")
}

private fun decodeExpressionBitmap(encoded: String): Bitmap {
    require(encoded.length in 16..ExpressionPack.MAX_SVG)
    val bytes = Base64.getDecoder().decode(encoded)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val edge = EXPRESSION_LAYER_EDGE
    require(options.outMimeType == "image/webp" && options.outWidth in 1..edge && options.outHeight in 1..edge)
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    if (bitmap.width == bitmap.height) return bitmap
    val scale = edge.toFloat() / maxOf(bitmap.width, bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    return try {
        Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(bitmap, null, RectF((edge - w) / 2, (edge - h) / 2, (edge + w) / 2, (edge + h) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
        }
    } finally {
        bitmap.recycle()
    }
}

private data class ExpressionImageSource(val legacySvg: String?, val webp: String?)

private val expressionBitmapCache = object : LruCache<ExpressionImageSource, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: ExpressionImageSource, value: Bitmap) = value.allocationByteCount
}

internal fun renderExpressionArtwork(svg: String, bitmap: String?): Bitmap {
    val key = ExpressionImageSource(svg.takeIf { bitmap == null }, bitmap)
    return expressionBitmapCache.get(key) ?: (bitmap?.let(::decodeExpressionBitmap)
        ?: UiIcons.renderSvg(svg, EXPRESSION_LAYER_EDGE)).also { expressionBitmapCache.put(key, it) }
}

internal fun ExpressionEffect.withBitmapArtwork(): ExpressionEffect = copy(layers = layers.map { layer ->
    val encoded = layer.bitmapWebp ?: encodeExpressionBitmap(renderExpressionArtwork(layer.svg, null))
    layer.copy(svg = EMPTY_EXPRESSION_SVG, bitmapWebp = encoded)
})
