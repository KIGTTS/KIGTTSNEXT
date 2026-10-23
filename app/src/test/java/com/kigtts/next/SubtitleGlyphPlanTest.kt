package com.kigtts.next

import androidx.compose.ui.geometry.Rect
import com.kigtts.next.ui.home.*
import org.junit.Assert.*
import org.junit.Test

class SubtitleGlyphPlanTest {
    private fun glyphs(text: String) = text.mapIndexed { i, char -> SubtitleGlyph(i, i + 1, char.toString(), Rect(i * 10f, 0f, (i + 1) * 10f, 20f), 0) }
    @Test fun insertionRetainsBothSidesAndOnlyTheNewGlyphEnters() {
        val moves = subtitleGlyphMoves(glyphs("甲乙"), glyphs("甲新乙"), 1)
        assertEquals(listOf(SubtitleGlyphMove(0, 0), SubtitleGlyphMove(null, 1), SubtitleGlyphMove(1, 2)), moves)
        assertEquals(listOf(SubtitleGlyphMove(0, 0), SubtitleGlyphMove(1, null), SubtitleGlyphMove(2, 1)),
            subtitleGlyphMoves(glyphs("甲新乙"), glyphs("甲乙"), 1))
    }
    @Test fun repeatedGlyphInsertionFollowsTheCaretAndLineHopsAreSeparate() {
        val moves = subtitleGlyphMoves(glyphs("哈哈"), glyphs("哈哈哈"), 1)
        assertEquals(SubtitleGlyphMove(null, 1), moves[1])
        val wrapped = glyphs("甲乙").mapIndexed { i, glyph -> glyph.copy(line = i) }
        assertTrue(subtitleGlyphMoves(glyphs("甲乙"), wrapped)[1].changesLine)
    }
}
