package com.kigtts.next.ui.home

import android.icu.text.BreakIterator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.Locale

internal data class SubtitleGlyph(val start: Int, val end: Int, val text: String, val bounds: Rect, val line: Int)
internal data class SubtitleGlyphFrame(val layout: TextLayoutResult, val origin: Offset, val glyphs: List<SubtitleGlyph>, val viewport: IntSize = layout.size)
internal data class SubtitleGlyphMove(val old: Int?, val next: Int?, val changesLine: Boolean = false)

/** Linear prefix/suffix matching: no quadratic diff or one composable per character. */
internal fun subtitleGlyphMoves(before: List<SubtitleGlyph>, after: List<SubtitleGlyph>, editStart: Int? = null): List<SubtitleGlyphMove> {
    var prefix = 0
    while (prefix < minOf(before.size, after.size) && before[prefix].text == after[prefix].text &&
        (editStart == null || before[prefix].end <= editStart)) prefix++
    var suffix = 0
    while (suffix < minOf(before.size, after.size) - prefix && before[before.lastIndex - suffix].text == after[after.lastIndex - suffix].text) suffix++
    return buildList {
        fun matched(old: Int, next: Int) { add(SubtitleGlyphMove(old, next, before[old].line != after[next].line)) }
        repeat(prefix) { matched(it, it) }
        for (index in prefix until before.size - suffix) add(SubtitleGlyphMove(index, null))
        for (index in prefix until after.size - suffix) add(SubtitleGlyphMove(null, index))
        repeat(suffix) { matched(before.size - suffix + it, after.size - suffix + it) }
    }
}

internal fun subtitleGlyphFrame(layout: TextLayoutResult, origin: IntOffset, viewport: IntSize = layout.size): SubtitleGlyphFrame {
    val text = layout.layoutInput.text.text
    val breaker = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val glyphs = buildList {
        var start = breaker.first(); var end = breaker.next()
        while (end != BreakIterator.DONE && size <= 240) {
            var box = layout.getBoundingBox(start)
            for (offset in start + 1 until end) {
                val part = layout.getBoundingBox(offset)
                box = Rect(minOf(box.left, part.left), minOf(box.top, part.top), maxOf(box.right, part.right), maxOf(box.bottom, part.bottom))
            }
            add(SubtitleGlyph(start, end, text.substring(start, end), box, layout.getLineForOffset(start)))
            start = end; end = breaker.next()
        }
    }
    return SubtitleGlyphFrame(layout, Offset(origin.x.toFloat(), origin.y.toFloat()), glyphs, viewport)
}

internal fun subtitleGlyphPosition(from: Offset, to: Offset, fraction: Float) = from + (to - from) * fraction
