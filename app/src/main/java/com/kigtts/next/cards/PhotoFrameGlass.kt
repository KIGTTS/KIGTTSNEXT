package com.kigtts.next.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.kigtts.next.media.GaussianBackdrop
import kotlin.math.roundToInt

/** Prepared photo samples. Any frame can paint these into its own outer margins. */
internal class PhotoFrameGlass private constructor(private val strips: Array<Bitmap?>,
    private val shared: Bitmap? = null, private val mainEdge: Int = 0, private val sideEdges: Boolean = false) : AutoCloseable {
    fun draw(canvas: Canvas, paper: RectF, photo: RectF) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val shade = Paint().apply { color = Color.argb(64, 0, 0, 0) }
        val areas = arrayOf(
            RectF(paper.left, paper.top, paper.right, photo.top),
            RectF(paper.left, photo.bottom, paper.right, paper.bottom),
            RectF(paper.left, photo.top, photo.left, photo.bottom),
            RectF(photo.right, photo.top, paper.right, photo.bottom),
        )
        areas.forEachIndexed { i, area ->
            val bitmap = (if (shared != null && (sideEdges || i == mainEdge)) shared else strips[i]) ?: return@forEachIndexed
            if (!area.isEmpty) {
                // Gap pixels belong to the frame; the sharp photo window is never painted over.
                canvas.drawBitmap(bitmap, null, area, paint)
                canvas.drawRect(area, shade)
            }
        }
    }

    override fun close() { shared?.recycle(); strips.forEach { it?.recycle() } }

    companion object {
        /** `photo` is the exact rendered/cropped photo region of `source`, not the surrounding paper. */
        fun prepare(source: Bitmap, photo: Rect, position: WatermarkPosition, band: Int, sideEdges: Boolean,
            sampling: WatermarkGlassSource = WatermarkGlassSource.AUTO): PhotoFrameGlass {
            val depth = (minOf(photo.width(), photo.height()) * (band + 8) / 100f).roundToInt().coerceAtLeast(1)
            val mainEdge = if (position == WatermarkPosition.TOP) 0 else 1
            val strips = arrayOfNulls<Bitmap>(4)
            if (sampling != WatermarkGlassSource.AUTO) {
                // Bake once even when the same sample fills several outer margins.
                val region = when (sampling) {
                    WatermarkGlassSource.TOP -> Rect(photo.left, photo.top, photo.right, minOf(photo.bottom, photo.top + depth))
                    WatermarkGlassSource.BOTTOM -> Rect(photo.left, maxOf(photo.top, photo.bottom - depth), photo.right, photo.bottom)
                    WatermarkGlassSource.LEFT -> Rect(photo.left, photo.top, minOf(photo.right, photo.left + depth), photo.bottom)
                    WatermarkGlassSource.RIGHT -> Rect(maxOf(photo.left, photo.right - depth), photo.top, photo.right, photo.bottom)
                    WatermarkGlassSource.FULL -> photo
                    WatermarkGlassSource.AUTO -> error("Automatic sampling does not have a shared region")
                }
                return PhotoFrameGlass(strips, GaussianBackdrop.blurRegion(source, region, sigma = 10f), mainEdge, sideEdges)
            }
            val side = (minOf(photo.width(), photo.height()) * .10f).roundToInt().coerceAtLeast(1)
            val regions = arrayOf(
                Rect(photo.left, photo.top, photo.right, minOf(photo.bottom, photo.top + if (position == WatermarkPosition.TOP) depth else side)),
                Rect(photo.left, maxOf(photo.top, photo.bottom - if (position == WatermarkPosition.BOTTOM) depth else side), photo.right, photo.bottom),
                Rect(photo.left, photo.top, minOf(photo.right, photo.left + side), photo.bottom),
                Rect(maxOf(photo.left, photo.right - side), photo.top, photo.right, photo.bottom),
            )
            try {
                regions.forEachIndexed { idx, region ->
                    if (sideEdges || idx == mainEdge) strips[idx] = GaussianBackdrop.blurRegion(source, region, sigma = 10f)
                }
                return PhotoFrameGlass(strips)
            } catch (failure: Throwable) {
                strips.forEach { it?.recycle() }
                throw failure
            }
        }

    }
}

internal fun RectF.framePixelBounds(bitmap: Bitmap): Rect {
    val x = left.roundToInt().coerceIn(0, bitmap.width - 1)
    val y = top.roundToInt().coerceIn(0, bitmap.height - 1)
    return Rect(x, y, right.roundToInt().coerceIn(x + 1, bitmap.width), bottom.roundToInt().coerceIn(y + 1, bitmap.height))
}
