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

/** A short, fixed horizontal fade at each line, independent of screen/font width. */
internal data class SubtitleLineHop(val from: SubtitleGlyphPose, val direction: Float, val started: Float, val distance: Float = 18f) {
    private fun fraction(now: Float) = ((now - started) / 200f).coerceIn(0f, 1f)
    fun finished(now: Float) = fraction(now) >= 1f
    fun outgoing(now: Float): SubtitleGlyphPose? {
        val phase = fraction(now) * 2f
        if (phase >= 1f) return null
        val edge = from.position.x + direction * distance
        return from.toward(from.copy(position = Offset(edge, from.position.y), alpha = 0f), FastOutSlowInEasing.transform(phase))
    }
    fun incoming(now: Float, target: SubtitleGlyphPose): SubtitleGlyphPose {
        val phase = (fraction(now) * 2f - 1f).coerceIn(0f, 1f)
        val edge = target.position.x - direction * distance
        return target.copy(position = Offset(edge, target.position.y), alpha = 0f)
            .toward(target, FastOutSlowInEasing.transform(phase))
    }
}
