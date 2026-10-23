package com.kigtts.next.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal data class SubtitleGlyphPose(val position: Offset, val size: Size, val alpha: Float = 1f) {
    fun toward(target: SubtitleGlyphPose, fraction: Float) = SubtitleGlyphPose(
        subtitleGlyphPosition(position, target.position, fraction),
        Size(size.width + (target.size.width - size.width) * fraction,
            size.height + (target.size.height - size.height) * fraction),
        alpha + (target.alpha - alpha) * fraction)
}

/** Exit beyond the viewport before entering from its opposite edge; never fly diagonally. */
internal data class SubtitleLineHop(val from: SubtitleGlyphPose, val direction: Float, val started: Float) {
    private fun fraction(now: Float) = ((now - started) / 240f).coerceIn(0f, 1f)
    fun finished(now: Float) = fraction(now) >= 1f
    fun outgoing(now: Float, width: Float): SubtitleGlyphPose? {
        val phase = fraction(now) * 2f
        if (phase >= 1f) return null
        val edge = if (direction > 0f) width + from.size.width else -from.size.width * 2f
        return from.toward(from.copy(position = Offset(edge, from.position.y), alpha = 0f), FastOutSlowInEasing.transform(phase))
    }
    fun incoming(now: Float, width: Float, target: SubtitleGlyphPose): SubtitleGlyphPose {
        val phase = (fraction(now) * 2f - 1f).coerceIn(0f, 1f)
        val edge = if (direction > 0f) -target.size.width * 2f else width + target.size.width
        return target.copy(position = Offset(edge, target.position.y), alpha = 0f)
            .toward(target, FastOutSlowInEasing.transform(phase))
    }
}
