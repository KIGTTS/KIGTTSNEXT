package com.kigtts.next.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText

@Composable
internal fun SubtitleGlyphCanvas(motion: SubtitleGlyphMotion, visible: Boolean, modifier: Modifier) {
    val first = rememberGraphicsLayer(); val second = rememberGraphicsLayer()
    val recorded = remember { arrayOfNulls<TextLayoutResult>(2) }
    val opacityPaint = remember { androidx.compose.ui.graphics.Paint() }
    Canvas(modifier) {
        if (!visible) return@Canvas
        val next = motion.frame ?: return@Canvas
        val before = motion.old
        val progress = motion.progress
        // Recording the paragraph once preserves shaping, rich spans and GPU text caches.
        fun layer(frame: SubtitleGlyphFrame, index: Int): GraphicsLayer {
            val result = if (index == 0) first else second
            if (recorded[index] !== frame.layout) {
                result.record(size = frame.layout.size) { drawText(frame.layout) }
                recorded[index] = frame.layout
            }
            return result
        }
        if (!motion.running) {
            translate(next.origin.x, next.origin.y) { drawText(next.layout) }
            return@Canvas
        }
        val active = layer(next, 1)
        fun glyph(frame: SubtitleGlyphFrame, index: Int, source: GraphicsLayer, pose: SubtitleGlyphPose) {
            if (pose.alpha <= .001f) return
            val bounds = frame.glyphs[index].bounds
            if (bounds.width <= 0 || bounds.height <= 0) return
            translate(pose.position.x, pose.position.y) {
                scale(pose.size.width / bounds.width, pose.size.height / bounds.height, Offset.Zero) {
                    // Alpha is isolated per glyph; both recorded paragraphs remain reusable.
                    if (pose.alpha < .999f) { opacityPaint.alpha = pose.alpha; drawContext.canvas.saveLayer(Rect(0f, 0f, bounds.width, bounds.height), opacityPaint) }
                    clipRect(0f, 0f, bounds.width, bounds.height) { translate(-bounds.left, -bounds.top) { drawLayer(source) } }
                    if (pose.alpha < .999f) drawContext.canvas.restore()
                }
            }
        }
        if (before != null && progress < 1f) {
            val previous = layer(before, 0)
            motion.moves.filter { it.next == null && it.old != null }.forEach { move ->
                    val start = motion.departures.getValue(move.old!!)
                    val end = start.copy(position = start.position - Offset(0f, start.size.height * .12f),
                        size = start.size * .94f, alpha = 0f)
                    glyph(before, move.old, previous, start.toward(end, progress))
            }
        }
        next.glyphs.indices.forEach { index ->
            motion.outgoing(index)?.let { glyph(next, index, active, it) }
            glyph(next, index, active, motion.pose(index))
        }
    }
}
