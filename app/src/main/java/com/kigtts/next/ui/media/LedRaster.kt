package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.IntSize
import com.kigtts.plugin.api.LedAlignment
import com.kigtts.plugin.api.NativeSubtitleRenderer.Led
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

private const val TILE = 512
private const val MASK_PIXEL_BUDGET = 4_000_000f
private const val GRID_CELL_BUDGET = 65_000f

internal fun ledLampPitch(board: IntSize, density: Float, displayHeight: Float, rows: Int): Float =
    maxOf(1.5f * density, sqrt(board.width.toFloat() * board.height / GRID_CELL_BUDGET), displayHeight / rows)

private data class LedTypeSize(val font: Float, val height: Float)

private fun ledTypeSize(fontWidth: Float, height: Int, percent: Float, style: Led): LedTypeSize {
    val displayHeight = height * style.displayHeight
    val limit = (displayHeight / 1.18f).coerceAtLeast(1f)
    val preferred = fontWidth * percent / 100f
    return LedTypeSize(preferred.coerceIn(1f, limit), displayHeight)
}

internal class LedRaster(
    val board: IntSize,
    private val safe: MediaSafeArea,
    private val style: Led,
    private val percent: Float,
    private val fontWidth: Float,
    private val density: Float,
    private val unit: Float,
    private val textWidth: Int,
    private val textHeight: Int,
    private val matrix: LedLampGrid?,
    private val plainTiles: List<LedTextTile>,
) {
    private val width = board.width - safe.left - safe.right
    private val height = board.height - safe.top - safe.bottom
    private val committed = ledTypeSize(fontWidth, height, percent, style)
    private val viewport = RectF()
    private val background = Paint().also { it.color = style.background.toInt() }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun period(gap: Float, bounds: IntSize = board, insets: MediaSafeArea = safe, preview: Float = percent): Float {
        val availableHeight = bounds.height - insets.top - insets.bottom
        val availableWidth = bounds.width - insets.left - insets.right
        if (availableWidth < 4 || availableHeight < 4) return 0f
        val type = ledTypeSize(fontWidth, availableHeight, preview, style)
        val sample = unit * type.font / committed.font
        return if (style.wrap) (textHeight * sample - type.height).coerceAtLeast(0f)
        else if (textWidth * sample > availableWidth) textWidth * sample + gap else 0f
    }

    fun draw(canvas: Canvas, offset: Float, gap: Float, preview: Float = percent, bounds: IntSize = board, insets: MediaSafeArea = safe) {
        canvas.drawRect(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat(), background)
        val width = bounds.width - insets.left - insets.right
        val height = bounds.height - insets.top - insets.bottom
        if (width < 4 || height < 4) return
        val type = if (height == this.height && preview == percent) committed else ledTypeSize(fontWidth, height, preview, style)
        val scale = type.font / committed.font
        val sampleUnit = unit * scale
        val visible = textWidth * sampleUnit
        val viewTop = insets.top + (height - type.height) / 2f
        viewport.set(0f, 0f, width.toFloat(), type.height)
        val x = if (style.wrap || visible > width) 0f else when (style.alignment) {
            LedAlignment.START -> 0f
            LedAlignment.CENTER -> (width - visible) / 2f
            LedAlignment.END -> width - visible
        }
        val y = ((type.height - textHeight * sampleUnit) / 2f).coerceAtLeast(0f)
        var drawX = x
        var drawY = y
        var repeatX = Float.NaN
        if (style.wrap) drawY -= offset.coerceIn(0f, (textHeight * sampleUnit - type.height).coerceAtLeast(0f))
        else if (visible > width) {
            val length = visible + gap
            val shift = offset % length
            drawX += if (style.scrollRight) shift else -shift
            repeatX = drawX + if (style.scrollRight) -length else length
        }
        canvas.save()
        canvas.translate(insets.left.toFloat(), viewTop)
        canvas.clipRect(viewport)
        if (matrix != null) {
            val area = IntSize(width, ceil(type.height).toInt())
            matrix.resize(area, ledLampPitch(area, density, type.height, style.dotRows))
            matrix.drawUnlit(canvas)
            matrix.drawLit(canvas, viewport, drawX, drawY, sampleUnit, repeatX)
        } else {
            drawPlain(canvas, drawX, drawY, sampleUnit)
            if (repeatX.isFinite()) drawPlain(canvas, repeatX, drawY, sampleUnit)
        }
        canvas.restore()
    }

    private fun drawPlain(canvas: Canvas, x: Float, y: Float, unit: Float) {
        plainTiles.forEach { tile ->
            val left = x + tile.left * unit
            val top = y + tile.top * unit
            if (left >= viewport.right || left + tile.bitmap.width * unit <= viewport.left ||
                top >= viewport.bottom || top + tile.bitmap.height * unit <= viewport.top) return@forEach
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(unit, unit)
            canvas.drawBitmap(tile.bitmap, 0f, 0f, bitmapPaint)
            canvas.restore()
        }
    }
}

internal data class LedTextTile(val left: Int, val top: Int, val bitmap: Bitmap)

internal suspend fun rasterLed(
    text: String, board: IntSize, safe: MediaSafeArea, percent: Float, density: Float, style: Led, typeface: Typeface,
    fontWidth: Float = (board.width - safe.left - safe.right).toFloat(),
): LedRaster? {
    val width = board.width - safe.left - safe.right
    val height = board.height - safe.top - safe.bottom
    if (width < 4 || height < 4) return null
    val type = ledTypeSize(fontWidth, height, percent, style)
    val area = IntSize(width, ceil(type.height).toInt())
    val pitch = ledLampPitch(area, density, type.height, style.dotRows)
    // Keep the glyph mask detailed enough to sample small strokes even on a coarse panel.
    var unit = maxOf(1f, if (style.dotMatrix) minOf(pitch / 3f, type.font / 24f) else 1f,
        type.font * sqrt(text.length.coerceAtLeast(1) / MASK_PIXEL_BUDGET))
    val layoutAlignment = when (style.alignment) {
        LedAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        LedAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        LedAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
    val content = if (style.wrap) text else text.replace(Regex("[\r\n\t]+"), " ")
    fun layoutAt(pixelUnit: Float): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = (type.font / pixelUnit).coerceAtLeast(.5f)
            this.typeface = typeface
            color = if (style.dotMatrix) Color.WHITE else style.foreground.toInt()
        }
        val cells = if (style.wrap) (width / pixelUnit).toInt().coerceAtLeast(1)
            else (ceil(paint.measureText(content)).toInt() + 2).coerceAtLeast(1)
        return StaticLayout.Builder.obtain(content, 0, content.length, paint, cells)
            .setAlignment(if (style.wrap) layoutAlignment else Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }
    var layout = layoutAt(unit)
    repeat(4) {
        val area = layout.width.toFloat() * layout.height
        if (area > MASK_PIXEL_BUDGET) {
            coroutineContext.ensureActive()
            unit *= sqrt(area / MASK_PIXEL_BUDGET) * 1.10f
            layout = layoutAt(unit)
        }
    }
    val maskWidth = layout.width
    val maskHeight = layout.height
    val mask = if (style.dotMatrix) LedInkMask(maskWidth, maskHeight) else null
    val argb = if (mask != null) IntArray(TILE * TILE) else null
    val tiles = buildList {
        for (top in 0 until maskHeight step TILE) for (left in 0 until maskWidth step TILE) {
            coroutineContext.ensureActive()
            val tileWidth = minOf(TILE, maskWidth - left)
            val tileHeight = minOf(TILE, maskHeight - top)
            val bitmap = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { translate(-left.toFloat(), -top.toFloat()); layout.draw(this) }
            if (mask == null || argb == null) add(LedTextTile(left, top, bitmap))
            else {
                bitmap.getPixels(argb, 0, tileWidth, 0, 0, tileWidth, tileHeight)
                mask.copyTile(argb, left, top, tileWidth, tileHeight)
                bitmap.recycle()
            }
        }
    }
    val grid = mask?.let { LedLampGrid(area, pitch, style, it) }
    return LedRaster(board, safe, style, percent, fontWidth, density, unit, maskWidth, maskHeight, grid, tiles)
}
