package com.kigtts.next.ui.onboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset

internal data class WelcomeBend(val cross: Float = 0f, val stretch: Float = 0f)

internal class WelcomeMesh(
    val image: Bitmap,
    private val bounds: Rect,
    private val root: Offset,
    private val axis: Offset,
    private val length: Float,
) {
    fun newVertices(): FloatArray = FloatArray((COLUMNS + 1) * (ROWS + 1) * 2)

    fun draw(canvas: Canvas, vertices: FloatArray, paint: Paint, bend: WelcomeBend) {
        var index = 0
        for (row in 0..ROWS) for (column in 0..COLUMNS) {
            val x = bounds.left + bounds.width().toFloat() * column / COLUMNS
            val y = bounds.top + bounds.height().toFloat() * row / ROWS
            val point = deformWelcomePoint(Offset(x, y), root, axis, length, bend)
            vertices[index++] = point.x
            vertices[index++] = point.y
        }
        canvas.drawBitmapMesh(image, COLUMNS, ROWS, vertices, 0, null, 0, paint)
    }

    private companion object {
        const val COLUMNS = 10
        const val ROWS = 12
    }
}

/** Root vertices stay put. The offset grows towards the free end of each limb. */
internal fun deformWelcomePoint(point: Offset, root: Offset, axis: Offset, length: Float, bend: WelcomeBend): Offset {
    val local = point - root
    val along = ((local.x * axis.x + local.y * axis.y) / length).coerceIn(0f, 1f)
    val weight = along * along
    return point + Offset(axis.x * bend.stretch - axis.y * bend.cross, axis.y * bend.stretch + axis.x * bend.cross) * weight
}

/** Record only the moving parts as tight, full-resolution textures, off the UI thread. */
internal fun recordWelcomeMeshes(layers: List<WelcomeLayer>): List<WelcomeLayer> {
    val width = WELCOME_ART_WIDTH
    val height = WELCOME_ART_HEIGHT
    val scratch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(scratch)
    val pixels = IntArray(width * height)
    try {
        return layers.map { layer ->
            val (pivot, direction, length) = when (layer.part) {
                WelcomePart.LEFT_EAR -> Triple(Offset(862f, 86f), Offset(-.77f, .64f), 420f)
                WelcomePart.RIGHT_EAR -> Triple(Offset(1098f, 73f), Offset(.78f, .62f), 460f)
                WelcomePart.LEFT_HAIR -> Triple(Offset(943f, 505f), Offset(-.44f, .90f), 630f)
                WelcomePart.RIGHT_HAIR -> Triple(Offset(1100f, 470f), Offset(.42f, .91f), 630f)
                WelcomePart.ARM -> Triple(Offset(898f, 720f), Offset(-.93f, -.37f), 810f)
                else -> return@map layer
            }
            scratch.eraseColor(0)
            canvas.drawPicture(layer.picture)
            scratch.getPixels(pixels, 0, width, 0, 0, width, height)
            val bounds = alphaBounds(pixels, width, height) ?: return@map layer
            val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, bounds.top * width + bounds.left, width, 0, 0, bounds.width(), bounds.height())
            layer.copy(mesh = WelcomeMesh(bitmap, bounds, pivot + Offset(0f, WELCOME_ORIGIN_Y),
                direction / direction.getDistance(), length))
        }
    } finally {
        scratch.recycle()
    }
}

private fun alphaBounds(pixels: IntArray, width: Int, height: Int): Rect? {
    var left = width
    var right = 0
    var top = height
    var bottom = 0
    for (y in 0 until height) {
        val start = y * width
        for (x in 0 until width) {
            if (pixels[start + x] ushr 24 == 0) continue
            left = minOf(left, x)
            right = maxOf(right, x + 1)
            top = minOf(top, y)
            bottom = y + 1
        }
    }
    if (left >= right || top >= bottom) return null
    return Rect((left - 2).coerceAtLeast(0), (top - 2).coerceAtLeast(0),
        (right + 2).coerceAtMost(width), (bottom + 2).coerceAtMost(height))
}
