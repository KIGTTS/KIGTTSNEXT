package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.unit.IntSize
import com.kigtts.plugin.api.NativeSubtitleRenderer.Led
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.abs

internal class LedInkMask(val width: Int, val height: Int) {
    private val pixels = ByteArray(width * height)

    fun copyTile(argb: IntArray, left: Int, top: Int, tileWidth: Int, tileHeight: Int) {
        for (row in 0 until tileHeight) {
            val source = row * tileWidth
            val destination = (top + row) * width + left
            for (column in 0 until tileWidth) pixels[destination + column] = (argb[source + column] ushr 24).toByte()
        }
    }

    private fun alpha(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x].toInt() and 255 else 0

    fun coverage(x: Float, y: Float, radius: Float): Int {
        val left = floor(x - radius).toInt()
        val right = floor(x + radius).toInt()
        val top = floor(y - radius).toInt()
        val bottom = floor(y + radius).toInt()
        val center = alpha(floor(x).toInt(), floor(y).toInt())
        return (alpha(left, top) + alpha(right, top) + alpha(left, bottom) + alpha(right, bottom) + center + 2) / 5
    }
}

/** The board shaders stay in card coordinates and the moving glyph switches whole cells on or off. */
internal class LedLampGrid(
    board: IntSize,
    initialPitch: Float,
    private val style: Led,
    private val ink: LedInkMask,
) {
    private var board = IntSize.Zero
    private var pitch = 0f
    private var columns = 0
    private var rows = 0
    private var stride = 0
    private var capacityRows = 0
    private var levels = IntArray(0)
    private lateinit var activation: Bitmap
    private lateinit var mask: BitmapShader
    private val transform = Matrix()
    private val lit = lampShader(style, lit = true)
    private val dark = lampShader(style, lit = false)
    private val unlit = Paint(Paint.FILTER_BITMAP_FLAG).also { it.shader = dark }
    // Avoid linearly sampling the binary mask on versions without per-shader filtering.
    private val composite = Paint()
    private val legacyMask = if (Build.VERSION.SDK_INT < 28) Paint().also {
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    } else null

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastUnit = Float.NaN
    private var lastRepeat = Float.NaN
    private val lastViewport = RectF()

    init { resize(board, initialPitch) }

    fun resize(next: IntSize, spacing: Float) {
        if (next == board && abs(spacing - pitch) < .0001f) return
        board = next
        pitch = spacing
        columns = ceil(next.width / pitch).toInt().coerceAtLeast(1)
        rows = ceil(next.height / pitch).toInt().coerceAtLeast(1)
        if (columns > stride || rows > capacityRows) {
            // Round both dimensions together so continuously resizing a panel reuses the same storage.
            stride = (columns + 31) / 32 * 32
            capacityRows = (rows + 31) / 32 * 32
            levels = IntArray(stride * capacityRows)
            activation = Bitmap.createBitmap(stride, capacityRows, Bitmap.Config.ARGB_8888)
            mask = BitmapShader(activation, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                if (Build.VERSION.SDK_INT >= 33) setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
            }
            legacyMask?.shader = mask
        }
        transform.setScale(pitch, pitch)
        mask.setLocalMatrix(transform)
        transform.setScale(pitch / LAMP_TEXTURE_SIZE, pitch / LAMP_TEXTURE_SIZE)
        lit.setLocalMatrix(transform)
        dark.setLocalMatrix(transform)
        composite.shader = if (Build.VERSION.SDK_INT >= 28) ComposeShader(lit, mask, PorterDuff.Mode.DST_IN) else lit
        lastX = Float.NaN
    }

    fun drawUnlit(canvas: Canvas) {
        canvas.drawRect(0f, 0f, board.width.toFloat(), board.height.toFloat(), unlit)
    }

    fun drawLit(canvas: Canvas, viewport: RectF, x: Float, y: Float, unit: Float, repeat: Float) {
        if (lastX != x || lastY != y || lastUnit != unit || lastRepeat.toBits() != repeat.toBits() ||
            lastViewport != viewport) {
            levels.fill(0, 0, stride * rows)
            projectGlyph(viewport, x, y, unit)
            if (repeat.isFinite()) projectGlyph(viewport, repeat, y, unit)
            activation.setPixels(levels, 0, stride, 0, 0, columns, rows)
            lastX = x
            lastY = y
            lastUnit = unit
            lastRepeat = repeat
            lastViewport.set(viewport)
        }
        if (legacyMask == null) canvas.drawRect(viewport, composite)
        else {
            val layer = canvas.saveLayer(viewport, null)
            canvas.drawRect(viewport, composite)
            canvas.drawRect(viewport, legacyMask)
            canvas.restoreToCount(layer)
        }
    }

    private fun projectGlyph(viewport: RectF, x: Float, y: Float, unit: Float) {
        val startCol = floor(maxOf(viewport.left, x) / pitch).toInt().coerceIn(0, columns)
        val endCol = ceil(minOf(viewport.right, x + ink.width * unit) / pitch).toInt().coerceIn(0, columns)
        val startRow = floor(maxOf(viewport.top, y) / pitch).toInt().coerceIn(0, rows)
        val endRow = ceil(minOf(viewport.bottom, y + ink.height * unit) / pitch).toInt().coerceIn(0, rows)
        val step = pitch / unit
        val radius = step * .28f
        val firstX = ((startCol + .5f) * pitch - x) / unit
        for (row in startRow until endRow) {
            val inkY = ((row + .5f) * pitch - y) / unit
            var inkX = firstX
            val rowStart = row * stride
            for (column in startCol until endCol) {
                if (ink.coverage(inkX, inkY, radius) >= 42) levels[rowStart + column] = -1
                inkX += step
            }
        }
    }
}

private const val LAMP_TEXTURE_SIZE = 256f

private fun lampShader(style: Led, lit: Boolean): BitmapShader {
    val side = LAMP_TEXTURE_SIZE.toInt()
    val image = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = side / 2f
    val radius = center * style.dotSize
    val base = style.foreground.toInt()
    if (lit && style.glow > 0f) {
        val tint = base and 0xffffff
        val alpha = (style.glow * 165).roundToInt()
        paint.shader = RadialGradient(center, center, center, intArrayOf(tint or (alpha shl 24), tint),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
    }
    paint.shader = null
    paint.color = if (lit) base else 0x267f7f7f
    if (style.squareDots) canvas.drawRect(center - radius, center - radius, center + radius, center + radius, paint)
    else canvas.drawCircle(center, center, radius, paint)
    return BitmapShader(image, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        if (Build.VERSION.SDK_INT >= 33) setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    }
}
