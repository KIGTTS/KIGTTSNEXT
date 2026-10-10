package com.kigtts.next.media.thumbnail

import android.graphics.*
import com.kigtts.next.media.*
import com.kigtts.next.media.smartcrop.CropImage
import kotlin.math.*

/** Cache this result: the compositor only scales/draws it, never runs a per-frame blur. */
object ThumbnailRenderer {
    fun render(source: Bitmap, width: Int, height: Int, entry: MediaEntry, check: () -> Unit = {}): Bitmap {
        require(width in 1..384 && height in 1..256)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val visibleWidth = (width * .66).roundToInt().coerceAtLeast(1)
        val region = ThumbnailFraming.region(CropImage(source.width, source.height, pixels), visibleWidth, height, entry, check)
        val chosen = Bitmap.createBitmap(source, region.left, region.top, region.width, region.height)
        val sharp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sharp); val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val left = width - visibleWidth.toFloat()
        fun shader(scale: Float, x: Float, y: Float): BitmapShader = BitmapShader(chosen, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate(x, y) })
        }
        val fill = max(visibleWidth.toFloat() / chosen.width, height.toFloat() / chosen.height)
        paint.shader = shader(fill, left + (visibleWidth - chosen.width * fill) / 2, (height - chosen.height * fill) / 2)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (region.fit) {
            val background = GaussianBackdrop.blur(sharp, maximum = 128, sigma = 8f)
            paint.shader = null; canvas.drawBitmap(background, null, Rect(0, 0, width, height), paint); background.recycle()
            val fit = min(visibleWidth.toFloat() / chosen.width, height.toFloat() / chosen.height)
            val x = left + (visibleWidth - chosen.width * fit) / 2; val y = (height - chosen.height * fit) / 2
            canvas.drawBitmap(chosen, null, RectF(x, y, x + chosen.width * fit, y + chosen.height * fit), paint)
        }
        if (chosen !== source) chosen.recycle()
        check()
        return ProgressiveThumbnailBlur.apply(sharp, check).also { sharp.recycle() }
    }
}
