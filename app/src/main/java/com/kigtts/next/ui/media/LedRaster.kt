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
import com.kigtts.next.state.SubtitleSize
import com.kigtts.plugin.api.LedAlignment
import com.kigtts.plugin.api.NativeSubtitleRenderer.Led
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

private const val TILE = 512
private const val MASK_PIXEL_BUDGET = 4_000_000f
private const val GRID_CELL_BUDGET = 65_000f

internal fun ledLampPitch(board: IntSize, density: Float, referenceFont: Float, rows: Int): Float {
    // Give the low end more room to distinguish coarse dots, while keeping fine grids and saved values usable.
    val coarseSpacing = (24f / rows).coerceAtLeast(1f).pow(.75f)
    return maxOf(1.5f * density, sqrt(board.width.toFloat() * board.height / GRID_CELL_BUDGET),
        referenceFont * 1.2f * coarseSpacing / rows)
}

private data class LedTypeSize(val font: Float, val height: Float)

private fun ledTypeSize(width: Int, height: Int, percent: Float, style: Led): LedTypeSize {
    val fill = if (style.wrap) 0f else max((style.fontScale - 1f) / 2f, (percent - 28f) / 22f).coerceIn(0f, 1f)
    val displayHeight = height * (style.displayHeight + (1f - style.displayHeight) * fill)
    val limit = (displayHeight / 1.18f).coerceAtLeast(1f)
    val preferred = width * percent * style.fontScale / 100f
    return LedTypeSize((preferred + (limit - preferred) * fill).coerceIn(1f, limit), displayHeight)
}

internal class LedRaster(
    private val board: IntSize,
    private val safe: MediaSafeArea,
    private val style: Led,
    private val percent: Float,
    private val unit: Float,
    private val textWidth: Int,
    private val textHeight: Int,
    private val matrix: LedLampGrid?,
    private val plainTiles: List<LedTextTile>,
) {
    private val width = board.width - safe.left - safe.right
    private val height = board.height - safe.top - safe.bottom
    private val committed = ledTypeSize(width, height, percent, style)
    private val viewport = RectF()
    private val background = Paint().also { it.color = style.background.toInt() }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val distance = if (style.wrap) (textHeight * unit - committed.height).coerceAtLeast(0f)
        else (textWidth * unit - width).coerceAtLeast(0f)

    fun period(gap: Float) = if (style.wrap) distance else textWidth * unit + gap

    fun draw(canvas: Canvas, offset: Float, gap: Float, preview: Float = percent) {
        canvas.drawRect(0f, 0f, board.width.toFloat(), board.height.toFloat(), background)
        matrix?.drawUnlit(canvas)
        val type = if (preview == percent) committed else ledTypeSize(width, height, preview, style)
        val scale = type.font / committed.font
        val sampleUnit = unit * scale
        val visible = textWidth * sampleUnit
        val viewTop = safe.top + (height - type.height) / 2f
        viewport.set(safe.left.toFloat(), viewTop, (board.width - safe.right).toFloat(), viewTop + type.height)
        val x = safe.left + if (style.wrap || visible > width) 0f else when (style.alignment) {
            LedAlignment.START -> 0f
            LedAlignment.CENTER -> (width - visible) / 2f
            LedAlignment.END -> width - visible
        }
        val y = viewTop + ((type.height - textHeight * sampleUnit) / 2f).coerceAtLeast(0f)
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
        canvas.clipRect(viewport)
        if (matrix != null) matrix.drawLit(canvas, viewport, drawX, drawY, sampleUnit, repeatX)
        else {
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
): LedRaster? {
    val width = board.width - safe.left - safe.right
    val height = board.height - safe.top - safe.bottom
    if (width < 4 || height < 4) return null
    val type = ledTypeSize(width, height, percent, style)
    val reference = ledTypeSize(width, height, SubtitleSize.DEFAULT_PERCENT, style)
    // Density settings determine the physical board. Zoom only changes the glyph projected onto it.
    val pitch = ledLampPitch(board, density, reference.font, style.dotRows)
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
            if (!style.dotMatrix && style.glow > 0f) setShadowLayer(textSize * style.glow * .16f, 0f, 0f, color)
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
    val grid = mask?.let { LedLampGrid(board, pitch, style, it) }
    return LedRaster(board, safe, style, percent, unit, maskWidth, maskHeight, grid, tiles)
}
