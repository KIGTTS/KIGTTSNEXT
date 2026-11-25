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
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.IntSize
import com.kigtts.plugin.api.LedAlignment
import com.kigtts.plugin.api.NativeSubtitleRenderer.Led
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private const val TILE = 512
private const val MASK_PIXEL_BUDGET = 4_000_000f

internal data class LedTile(val x: Float, val y: Float, val width: Float, val height: Float,
    val paint: Paint, val legacyMask: Paint? = null)

internal class LedRaster(
    private val tiles: List<LedTile>,
    private val style: Led,
    private val board: IntSize,
    private val viewport: android.graphics.RectF,
    private val boardDots: Paint?,
    val distance: Float,
    private val extent: Float,
    private val left: Float,
    private val top: Float,
) {
    private val background = Paint().also { it.color = style.background.toInt() }
    fun period(gap: Float) = if (style.wrap) distance else extent + gap

    fun draw(canvas: Canvas, offset: Float, gap: Float) {
        canvas.drawRect(0f, 0f, board.width.toFloat(), board.height.toFloat(), background)
        boardDots?.let { canvas.drawRect(0f, 0f, board.width.toFloat(), board.height.toFloat(), it) }
        canvas.save()
        canvas.clipRect(viewport)
        if (distance <= 0f) drawTiles(canvas, left, top)
        else if (style.wrap) drawTiles(canvas, left, top - offset.coerceAtMost(distance))
        else {
            val x = if (style.scrollRight) offset else -offset
            val period = extent + gap
            drawTiles(canvas, left + x, top)
            drawTiles(canvas, left + x + if (style.scrollRight) -period else period, top)
        }
        canvas.restore()
    }

    private fun drawTiles(canvas: Canvas, x: Float, y: Float) {
        tiles.forEach { tile ->
            val start = x + tile.x
            val top = y + tile.y
            if (start >= viewport.right || start + tile.width <= viewport.left ||
                top >= viewport.bottom || top + tile.height <= viewport.top) return@forEach
            canvas.save()
            canvas.translate(start, top)
            val mask = tile.legacyMask
            if (mask == null) canvas.drawRect(0f, 0f, tile.width, tile.height, tile.paint)
            else {
                val layer = canvas.saveLayer(0f, 0f, tile.width, tile.height, null)
                canvas.drawRect(0f, 0f, tile.width, tile.height, tile.paint)
                canvas.drawRect(0f, 0f, tile.width, tile.height, mask)
                canvas.restoreToCount(layer)
            }
            canvas.restore()
        }
    }
}

internal suspend fun rasterLed(
    text: String, board: IntSize, safe: MediaSafeArea, percent: Float, density: Float, style: Led, typeface: Typeface,
): LedRaster? {
    val width = board.width - safe.left - safe.right
    val height = board.height - safe.top - safe.bottom
    if (width < 4 || height < 4) return null
    val fill = if (style.wrap) 0f else max((style.fontScale - 1f) / 2f, (percent - 28f) / 22f).coerceIn(0f, 1f)
    val displayHeight = height * (style.displayHeight + (1f - style.displayHeight) * fill)
    val preferred = width * percent * style.fontScale / 100f
    val fontPx = (preferred + (displayHeight / 1.18f - preferred) * fill).coerceIn(1f, displayHeight.coerceAtLeast(1f) / 1.18f)
    val minPitch = if (style.dotMatrix) maxOf(2f * density, sqrt(board.width.toFloat() * board.height / 24_000f),
        fontPx * 1.2f / style.dotRows) else 1f
    val pitch = max(minPitch, fontPx * sqrt(text.length.coerceAtLeast(1) / MASK_PIXEL_BUDGET))
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontPx / pitch
        this.typeface = typeface
        color = if (style.dotMatrix) Color.WHITE else style.foreground.toInt()
        if (!style.dotMatrix && style.glow > 0f) setShadowLayer(textSize * style.glow * .16f, 0f, 0f, color)
    }
    val content = if (style.wrap) text else text.replace(Regex("[\r\n\t]+"), " ")
    val viewportCells = (width / pitch).toInt().coerceAtLeast(1)
    val textCells = if (style.wrap) viewportCells else (ceil(paint.measureText(content)).toInt() + 2).coerceAtLeast(1)
    val layoutAlignment = when (style.alignment) {
        LedAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        LedAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        LedAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
    val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, textCells)
        .setAlignment(if (style.wrap) layoutAlignment else Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false).build()
    val light = if (style.dotMatrix) lampShader(style, pitch, true) else null
    // Android 8 cannot combine two bitmap shaders in a hardware-accelerated ComposeShader.
    val legacyComposite = light != null && Build.VERSION.SDK_INT < 28
    val dark = if (style.dotMatrix) Paint().apply { shader = lampShader(style, pitch, false) } else null
    val tiles = buildList {
        for (top in 0 until layout.height step TILE) for (left in 0 until textCells step TILE) {
            coroutineContext.ensureActive()
            // Only the lines intersecting this tile are visited by StaticLayout.draw.
            val tileWidth = minOf(TILE, textCells - left)
            val tileHeight = minOf(TILE, layout.height - top)
            val mask = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
            Canvas(mask).apply { translate(-left.toFloat(), -top.toFloat()); layout.draw(this) }
            val bitmap = BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(pitch, pitch) })
            }
            val framePaint = Paint(if (style.dotMatrix) 0 else Paint.FILTER_BITMAP_FLAG).apply {
                shader = when {
                    light == null -> bitmap
                    legacyComposite -> light
                    else -> ComposeShader(light, bitmap, PorterDuff.Mode.DST_IN)
                }
            }
            val legacyMask = if (legacyComposite) Paint().apply {
                shader = bitmap
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            } else null
            add(LedTile(left * pitch, top * pitch, tileWidth * pitch, tileHeight * pitch, framePaint, legacyMask))
        }
    }
    val textWidth = textCells * pitch
    val textHeight = layout.height * pitch
    val viewTop = safe.top + (height - displayHeight) / 2
    val overflow = if (style.wrap) (textHeight - displayHeight).coerceAtLeast(0f) else (textWidth - width).coerceAtLeast(0f)
    val x = safe.left + if (style.wrap || overflow > 0f) 0f else when (style.alignment) {
        LedAlignment.START -> 0f
        LedAlignment.CENTER -> (width - textWidth) / 2
        LedAlignment.END -> width - textWidth
    }
    val y = viewTop + ((displayHeight - textHeight) / 2).coerceAtLeast(0f)
    return LedRaster(tiles, style, board, android.graphics.RectF(safe.left.toFloat(), viewTop,
        (board.width - safe.right).toFloat(), viewTop + displayHeight), dark, overflow, textWidth, x, y)
}

private fun lampShader(style: Led, pitch: Float, lit: Boolean): BitmapShader {
    val side = 24
    val image = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = side / 2f
    val radius = center * style.dotSize
    if (lit && style.glow > 0f) {
        val tint = style.foreground.toInt() and 0xffffff
        val alpha = floor(style.glow * 170).toInt()
        paint.shader = RadialGradient(center, center, center, intArrayOf(tint or (alpha shl 24), tint), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
        paint.shader = null
    }
    paint.color = if (lit) style.foreground.toInt() else 0x267f7f7f
    if (style.squareDots) canvas.drawRect(center - radius, center - radius, center + radius, center + radius, paint)
    else canvas.drawCircle(center, center, radius, paint)
    return BitmapShader(image, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(pitch / side, pitch / side) })
    }
}
