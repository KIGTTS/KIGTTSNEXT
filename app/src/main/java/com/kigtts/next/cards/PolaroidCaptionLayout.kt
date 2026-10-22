package com.kigtts.next.cards

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.kigtts.plugin.api.*

internal data class CaptionLine(val layout: StaticLayout, val x: Float, val y: Float)
internal class PolaroidCaptionLayout(val lines: List<CaptionLine>, val description: String) {
    fun draw(canvas: Canvas) { lines.forEach { line -> canvas.save(); canvas.translate(line.x, line.y); line.layout.draw(canvas); canvas.restore() } }
}

/** Shared by preview and export. One inline icon row follows the actual note in the logo-free column. */
internal fun polaroidCaption(card: PolaroidCard, geometry: PolaroidGeometry, captionWidth: Double,
    widthPx: Float, titleColor: Int, textColor: Int): PolaroidCaptionLayout {
    data class Text(val value: CharSequence, val size: Double, val maxLines: Int, val bold: Boolean = false, val info: Boolean = false)
    val fields = card.layers.filter { it.kind == CardLayerKind.PHOTO_INFO && it.text.isNotBlank() }
        .sortedBy { it.photoField()?.ordinal ?: Int.MAX_VALUE }
    val texts = buildList {
        if (card.title.isNotBlank()) add(Text(card.title, geometry.titleSize, 1, true))
        if (card.description.isNotBlank()) add(Text(card.description, geometry.descriptionSize, 3))
        if (fields.isNotEmpty()) add(Text(photoInformationText(fields), geometry.descriptionSize * .9, 1, info = true))
    }
    val unit = widthPx / geometry.paperWidth.toFloat()
    val left = (geometry.titleX * unit).toFloat()
    val top = (geometry.titleY * unit).toFloat()
    val bottom = ((geometry.paperHeight - if (geometry.millimeterSizing) 2.5 else 48.0) * unit).toFloat()
    val width = (captionWidth * unit).toInt().coerceAtLeast(1)
    fun layout(scale: Float): List<StaticLayout> = texts.map { value ->
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (value.size * unit * scale).toFloat().coerceAtLeast(.1f)
            color = if (value.bold) titleColor else textColor
            typeface = if (value.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        if (value.info) {
            val desired = Layout.getDesiredWidth(value.value, paint)
            if (desired > width) paint.textSize *= (width / desired).coerceAtLeast(.65f)
        }
        StaticLayout.Builder.obtain(value.value, 0, value.value.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.LTR)
            .setIncludePad(false).setLineSpacing(0f, 1.15f).setMaxLines(value.maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
    }
    var scale = 1f; var measured = layout(scale)
    fun gap() = (geometry.descriptionSize * unit * .2 * scale).toFloat()
    repeat(80) {
        if (measured.sumOf { it.height } + gap() * (measured.size - 1).coerceAtLeast(0) > bottom - top) {
            scale *= .94f; measured = layout(scale)
        }
    }
    var y = top
    val description = listOf(card.title, card.description,
        fields.joinToString(" · ") { layer -> listOfNotNull(layer.photoField()?.label, layer.text).joinToString(" ") })
        .filter(String::isNotBlank).joinToString("\n")
    return PolaroidCaptionLayout(measured.map { item -> CaptionLine(item, left, y).also { y += item.height + gap() } }, description)
}
