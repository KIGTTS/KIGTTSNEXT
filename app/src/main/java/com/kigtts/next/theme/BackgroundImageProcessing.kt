package com.kigtts.next.theme

import android.content.Context
import android.graphics.*
import hct.Hct
import java.io.File
import kotlin.math.*

/** Square master image: both device orientations share the same crop and center. */
data class BackgroundCrop(val x: Float = .5f, val y: Float = .5f, val zoom: Float = 1f) {
    fun bounds(width: Int, height: Int): RectF {
        val side = min(width, height) / zoom.coerceIn(1f, 8f)
        val cx = (x * width).coerceIn(side / 2, width - side / 2)
        val cy = (y * height).coerceIn(side / 2, height - side / 2)
        return RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
    }
    fun move(width: Int, height: Int, viewport: Float, panX: Float, panY: Float,
        scale: Float, focusX: Float = viewport / 2, focusY: Float = viewport / 2): BackgroundCrop {
        if (viewport <= 0) return this
        val before = bounds(width, height)
        val nextZoom = (zoom * scale).coerceIn(1f, 8f)
        val side = min(width, height) / nextZoom
        val cx = before.centerX() + (focusX / viewport - .5f) * (before.width() - side) - panX * side / viewport
        val cy = before.centerY() + (focusY / viewport - .5f) * (before.height() - side) - panY * side / viewport
        return BackgroundCrop(cx.coerceIn(side / 2, width - side / 2) / width,
            cy.coerceIn(side / 2, height - side / 2) / height, nextZoom)
    }
}

internal fun cropBackground(source: Bitmap, crop: BackgroundCrop): Bitmap {
    val bounds = crop.bounds(source.width, source.height)
    val side = min(2048, bounds.width().roundToInt()).coerceAtLeast(1)
    return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { output ->
        Canvas(output).apply { scale(side / bounds.width(), side / bounds.height()); translate(-bounds.left, -bounds.top)
            drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
    }
}

internal fun backgroundThemeSeed(bitmap: Bitmap): Long {
    val sample = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    try {
        val pixels = IntArray(4096).also { sample.getPixels(it, 0, 64, 0, 0, 64, 64) }
        val bins = pixels.asSequence().groupingBy { (((it shr 20) and 15) shl 8) or (((it shr 12) and 15) shl 4) or ((it shr 4) and 15) }.eachCount()
        val winner = bins.maxByOrNull { (rgb, count) ->
            val argb = Color.rgb((rgb shr 8 and 15) * 17, (rgb shr 4 and 15) * 17, (rgb and 15) * 17)
            val hct = Hct.fromInt(argb)
            count * (1.0 + min(hct.chroma, 48.0) / 24.0) * if (hct.tone in 12.0..90.0) 1.0 else .3
        }?.key ?: 0x066
        return Color.rgb((winner shr 8 and 15) * 17, (winner shr 4 and 15) * 17, (winner and 15) * 17).toLong() and 0xffffffffL
    } finally { if (sample !== bitmap) sample.recycle() }
}

/** A versioned disk cache; expensive work only happens on import or cache miss, on Dispatchers.IO. */
internal class BackgroundBlurCache(context: Context) {
    private val assets = AppearanceAssets(context)
    private val root = File(context.cacheDir, "appearance-blur-f32-dither-v3").apply { mkdirs() }
    fun removeTemporary(source: File) = synchronized(lock) {
        source.delete()
        for (blur in listOf(BackgroundBlur.PAGE, BackgroundBlur.CARD)) output(source, blur).delete()
    }

    private fun output(source: File, blur: BackgroundBlur) = File(root, blur.name.lowercase() + "-" + source.nameWithoutExtension + ".png")

    fun file(key: String?, blur: BackgroundBlur = BackgroundBlur.CARD): File? {
        require(blur != BackgroundBlur.NONE)
        val source = assets.file(key) ?: return null
        val output = output(source, blur)
        if (output.length() > 0) return output
        synchronized(lock) {
            if (output.length() > 0) return output
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (max(options.outWidth, options.outHeight) / options.inSampleSize > 384) options.inSampleSize *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, options) ?: return null
            val blurred = try { gaussianBlur(bitmap, blur.sigma) } finally { bitmap.recycle() }
            val temp = File(root, output.name + ".tmp")
            try { temp.outputStream().use { check(blurred.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                check(temp.renameTo(output)); return output
            } finally { blurred.recycle(); temp.delete() }
        }
    }
    companion object { private val lock = Any() }
}
