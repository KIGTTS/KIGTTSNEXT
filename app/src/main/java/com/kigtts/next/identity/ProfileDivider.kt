package com.kigtts.next.identity

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.sin

internal fun profileDividerPath(left: Float, right: Float, y: Float, wave: Boolean, amplitude: Float, period: Float): Path =
    Path().apply {
        moveTo(left, y)
        if (!wave || right <= left) lineTo(right, y)
        else {
            val waves = ((right - left) / period).toInt().coerceAtLeast(1)
            val count = waves * 12
            for (i in 1..count) {
                val t = i.toFloat() / count
                lineTo(left + (right - left) * t, y + (sin(2 * PI * waves * t) * amplitude).toFloat())
            }
        }
    }

internal fun profileDividerHtml(title: String, style: ProfileDividerStyle): String {
    val stroke = if (style == ProfileDividerStyle.WAVE) {
        val path = (0 until 8).joinToString(" ") { "q 3 -6 6 0 t 6 0" }
        """<svg width="100%" height="12" viewBox="0 0 96 12" preserveAspectRatio="none" aria-hidden="true"><path d="M0 6 $path" fill="none" stroke="currentColor" vector-effect="non-scaling-stroke"/></svg>"""
    } else """<svg width="100%" height="2" viewBox="0 0 96 2" preserveAspectRatio="none" aria-hidden="true"><path d="M0 1 H96" stroke="currentColor" vector-effect="non-scaling-stroke"/></svg>"""
    val label = title.takeIf(String::isNotBlank)?.let { "<strong>${com.kigtts.next.sharing.faceHtml(it)}</strong>" }
    return "<div style=\"display:flex;align-items:center;gap:10px;width:100%\"><span style=\"flex:1;min-width:0\">$stroke</span>" +
        (label?.let { "$it<span style=\"flex:1;min-width:0\">$stroke</span>" } ?: "") + "</div>"
}
