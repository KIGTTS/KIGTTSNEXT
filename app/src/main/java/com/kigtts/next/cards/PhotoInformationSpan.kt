package com.kigtts.next.cards

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.plugin.api.CardLayer
import kotlin.math.ceil

/** Inline vector spans keep the caption a real single line in previews, stills and live-photo exports. */
internal class PhotoInformationIconSpan(symbol: String) : ReplacementSpan() {
    private val path = MaterialSymbols.androidPath(symbol)
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?) =
        ceil(paint.textSize * 1.3f).toInt()
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        path?.let {
            canvas.save()
            canvas.translate(x, y - paint.textSize * .88f)
            canvas.scale(paint.textSize / 24f, paint.textSize / 24f)
            canvas.drawPath(it, paint)
            canvas.restore()
        }
    }
}

internal fun photoInformationText(fields: List<CardLayer>): CharSequence = SpannableStringBuilder().apply {
    fields.forEachIndexed { index, layer ->
        if (index > 0) append("   ")
        val start = length
        append('\uFFFC')
        setSpan(PhotoInformationIconSpan(layer.photoField()?.symbol ?: "info"), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(layer.text.replace(Regex("\\s+"), " ").trim())
    }
}
