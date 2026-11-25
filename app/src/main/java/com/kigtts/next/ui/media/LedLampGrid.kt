package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.sqrt

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
        return (alpha(left, top) + alpha(right, top) + alpha(left, bottom) + alpha(right, bottom) + 2) / 4
    }
}

private val lampLevels = IntArray(256) { level ->
    val alpha = (sqrt(level / 255f) * 255f).roundToInt()
    if (alpha == 0) 0 else (alpha shl 24) or 0xffffff
}

/** The board shaders always use card coordinates. The moving glyph only updates cell intensities. */
internal class LedLampGrid(
    board: IntSize,
    private val pitch: Float,
    style: Led,
    private val ink: LedInkMask,
) {
    private val width = board.width.toFloat()
    private val height = board.height.toFloat()
    private val columns = ceil(width / pitch).toInt()
    private val rows = ceil(height / pitch).toInt()
    private val levels = IntArray(columns * rows)
    private val activation = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
    private val mask = BitmapShader(activation, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().also { it.setScale(pitch, pitch) })
        if (Build.VERSION.SDK_INT >= 33) setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
    }
    private val lit = lampShader(style, pitch, lit = true)
    private val unlit = Paint(Paint.FILTER_BITMAP_FLAG).also { it.shader = lampShader(style, pitch, lit = false) }
    private val composite = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // Android 8 cannot hardware-accelerate a composition of two bitmap shaders.
        shader = if (Build.VERSION.SDK_INT >= 28) ComposeShader(lit, mask, PorterDuff.Mode.DST_IN) else lit
    }
    private val legacyMask = if (Build.VERSION.SDK_INT < 28) Paint().also {
        it.shader = mask
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    } else null

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastUnit = Float.NaN
    private var lastRepeat = Float.NaN
    private val lastViewport = RectF()

    fun drawUnlit(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width, height, unlit)
    }

    fun drawLit(canvas: Canvas, viewport: RectF, x: Float, y: Float, unit: Float, repeat: Float) {
        if (lastX != x || lastY != y || lastUnit != unit || lastRepeat.toBits() != repeat.toBits() ||
            lastViewport != viewport) {
            levels.fill(0)
            projectGlyph(viewport, x, y, unit)
            if (repeat.isFinite()) projectGlyph(viewport, repeat, y, unit)
            activation.setPixels(levels, 0, columns, 0, 0, columns, rows)
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
            val rowStart = row * columns
            for (column in startCol until endCol) {
                val coverage = ink.coverage(inkX, inkY, radius)
                if (coverage > 0) levels[rowStart + column] = lampLevels[coverage]
                inkX += step
            }
        }
    }
}

private fun lampShader(style: Led, pitch: Float, lit: Boolean): BitmapShader {
    val side = 64
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
    if (lit) paint.shader = RadialGradient(center - radius * .18f, center - radius * .24f, radius * 1.27f,
        intArrayOf(mixLedColor(base, Color.WHITE, .70f), base, mixLedColor(base, Color.BLACK, .32f)),
        floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP)
    else {
        paint.shader = null
        paint.color = 0x267f7f7f
    }
    if (style.squareDots) canvas.drawRect(center - radius, center - radius, center + radius, center + radius, paint)
    else canvas.drawCircle(center, center, radius, paint)
    return BitmapShader(image, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().also { it.setScale(pitch / side, pitch / side) })
        if (Build.VERSION.SDK_INT >= 33) setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    }
}

private fun mixLedColor(from: Int, to: Int, fraction: Float): Int {
    fun mix(a: Int, b: Int) = (a + (b - a) * fraction).roundToInt()
    return Color.argb(Color.alpha(from), mix(Color.red(from), Color.red(to)),
        mix(Color.green(from), Color.green(to)), mix(Color.blue(from), Color.blue(to)))
}
