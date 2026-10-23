package com.kigtts.next

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.home.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SubtitleGlyphMotionTest {
    private val measurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext<Application>()), Density(1f), LayoutDirection.Ltr)
    private fun frame(text: String, font: Int = 40, width: Int = 400): SubtitleGlyphFrame {
        val layout = measurer.measure(text, TextStyle(fontSize = font.sp, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width))
        return subtitleGlyphFrame(layout, IntOffset(0, 120), IntSize(width, 500))
    }
    @Test fun reducedMotionSnapsTypingAndResizingButKeepsClearAndQuickTextFades() {
        val motion = SubtitleGlyphMotion()
        val reduced = SubtitleGlyphPolicy(reducedMotion = true)
        motion.update(frame("甲"), 1, true, policy = reduced)
        motion.update(frame("甲乙"), 2, true, policy = reduced)
        assertFalse(motion.running)
        motion.update(frame("甲乙", 72, 100), 2, true, policy = reduced)
        assertFalse(motion.running)
        motion.update(frame(""), 0, true, policy = reduced)
        assertTrue(motion.running); assertTrue(motion.fadeOnly)
        motion.advanceBy(200f)
        motion.update(frame("快捷文本"), 4, true, policy = reduced.copy(presentationRevision = 1))
        assertTrue(motion.running); assertTrue(motion.fadeOnly)
        assertNull(motion.outgoing(0))
    }
    @Test fun displayPolicyAnimatesOnlyReflowAndDisablingItSettlesTheSameFrameImmediately() {
        val motion = SubtitleGlyphMotion()
        val display = SubtitleGlyphPolicy(layoutChangesOnly = true)
        motion.update(frame("甲乙"), null, true, policy = display)
        val text = frame("甲乙丙丁", width = 200)
        motion.update(text, null, true, policy = display)
        assertFalse("Presentation fade already owns text changes", motion.running)
        val narrow = frame("甲乙丙丁", width = 100)
        motion.update(narrow, null, true, policy = display)
        assertTrue(motion.running)
        motion.update(narrow, null, false, policy = display)
        assertFalse(motion.running)
        narrow.glyphs.forEachIndexed { index, glyph -> assertEquals(glyph.bounds.topLeft + narrow.origin, motion.pose(index).position) }
    }
    @Test fun aSecondKeystrokeContinuesFromTheVisiblePositionSizeAndOpacity() {
        val motion = SubtitleGlyphMotion()
        motion.update(frame("甲乙"), 2, true)
        motion.update(frame("甲新乙", 36), 2, true)
        assertEquals(0f, motion.progress, 0f)
        motion.advanceBy(64f)
        val first = motion.pose(0); val inserted = motion.pose(1)
        assertTrue(inserted.alpha > 0f && inserted.alpha < 1f)
        motion.update(frame("甲新字乙", 32), 3, true)
        assertEquals(first, motion.pose(0))
        assertEquals(inserted, motion.pose(1))
        assertEquals(0f, motion.pose(2).alpha, 0f)
    }
    @Test fun zoomAppliesTheNewSizeAndLayoutTogetherForGlyphsStayingOnTheirLine() {
        val motion = SubtitleGlyphMotion()
        motion.update(frame("甲乙", 30), 2, true)
        val enlarged = frame("甲乙", 72)
        motion.update(enlarged, 2, true)
        assertFalse(motion.running)
        enlarged.glyphs.forEachIndexed { index, glyph ->
            assertEquals(glyph.bounds.topLeft + enlarged.origin, motion.pose(index).position)
            assertEquals(glyph.bounds.size, motion.pose(index).size)
        }
    }
    @Test fun reflowUsesAShortFixedDistanceAndContinuousZoomDoesNotRestartIt() {
        val motion = SubtitleGlyphMotion()
        val initial = frame("甲乙丙丁", width = 200)
        val wrapped = frame("甲乙丙丁", width = 100)
        motion.update(initial, 4, true)
        motion.update(wrapped, 4, true)
        val index = initial.glyphs.indices.first { initial.glyphs[it].line != wrapped.glyphs[it].line }
        assertEquals(initial.glyphs[index].bounds.topLeft + initial.origin, motion.outgoing(index)!!.position)
        motion.advanceBy(80f)
        val outgoing = motion.outgoing(index)!!
        val startX = initial.glyphs[index].bounds.left + initial.origin.x
        assertTrue(outgoing.position.x > startX)
        assertTrue(outgoing.position.x <= startX + 18f)
        assertEquals(0f, motion.pose(index).alpha, 0f)
        motion.advanceBy(40f)
        assertNull(motion.outgoing(index))
        val targetX = wrapped.glyphs[index].bounds.left + wrapped.origin.x
        assertTrue(motion.pose(index).position.x in targetX - 18f..targetX)
        assertTrue(motion.pose(index).alpha > 0f)
        repeat(7) {
            motion.update(frame("甲乙丙丁", font = 39, width = 99 - it), 4, true)
            motion.advanceBy(16f)
        }
        assertFalse("Continuous layout updates must not starve the line transition", motion.running)
        assertEquals(1f, motion.pose(index).alpha, 0f)
    }
    @Test fun reverseReflowUsesTheSameDistanceForDifferentFontAndViewportSizes() {
        val start = SubtitleGlyphPose(Offset(20f, 180f), Size(40f, 50f))
        val hop = SubtitleLineHop(start, -1f, 0f)
        val target = start.copy(position = Offset(250f, 90f))
        assertTrue(hop.outgoing(80f)!!.position.x in 2f..20f)
        assertEquals(0f, hop.incoming(100f, target).alpha, 0f)
        val entering = hop.incoming(140f, target)
        assertTrue(entering.position.x in 250f..268f)
        assertEquals(90f, entering.position.y, 0f)
        assertEquals(target, hop.incoming(200f, target))
        val larger = target.copy(position = Offset(900f, 250f), size = Size(120f, 150f))
        assertEquals(entering.position.x - target.position.x, hop.incoming(140f, larger).position.x - larger.position.x, .001f)
    }
    @Test fun rapidlyReversingResizeFinishesEachGlyphFadeWithoutRestartingOrBlinking() {
        val motion = SubtitleGlyphMotion()
        motion.update(frame("甲乙丙丁", width = 200), 4, true)
        motion.update(frame("甲乙丙丁", width = 100), 4, true)
        repeat(32) { tick ->
            motion.advanceBy(16f)
            val next = frame("甲乙丙丁", width = if (tick % 2 == 0) 200 else 100)
            motion.update(next, 4, true)
            if (tick >= 13) next.glyphs.indices.forEach { index ->
                assertNull("Repeated threshold crossings must not restart departures", motion.outgoing(index))
                assertEquals("Completed glyphs stay opaque while the divider keeps moving", 1f, motion.pose(index).alpha, .001f)
            }
        }
        motion.advanceBy(250f)
        motion.update(frame("甲乙丙丁", width = 200), 4, true)
        assertTrue("A separate resize gesture may animate again", motion.running)
    }
    @Test fun insertingAndRemovingAWholeRowMovesExistingRowsSmoothlyWithoutFadingThem() {
        for ((text, changed) in listOf("甲乙\n丙丁" to "新\n甲乙\n丙丁", "新\n甲乙\n丙丁" to "甲乙\n丙丁")) {
            val motion = SubtitleGlyphMotion()
            val before = frame(text)
            val after = frame(changed)
            motion.update(before, text.length, true)
            val oldIndex = before.glyphs.indexOfFirst { it.text == "甲" }
            val nextIndex = after.glyphs.indexOfFirst { it.text == "甲" }
            val start = motion.pose(oldIndex).position
            val end = after.glyphs[nextIndex].bounds.topLeft + after.origin
            motion.update(after, if (changed.startsWith("新")) 2 else 0, true)
            assertNull("A complete row must move, not dissolve", motion.outgoing(nextIndex))
            assertEquals(start, motion.pose(nextIndex).position)
            motion.advanceBy(64f)
            val middle = motion.pose(nextIndex)
            assertEquals(1f, middle.alpha, 0f)
            assertTrue(middle.position.y > minOf(start.y, end.y) && middle.position.y < maxOf(start.y, end.y))
            motion.advanceBy(140f)
            assertEquals(end, motion.pose(nextIndex).position)
        }
    }
    @Test fun addingARowDuringResizeMovesUnaffectedTextWhileApplyingItsFontSizeImmediately() {
        val motion = SubtitleGlyphMotion()
        val initial = frame("甲乙丙丁", font = 30, width = 160)
        val wrapped = frame("甲乙丙丁", font = 48, width = 120).copy(origin = Offset(0f, 80f))
        motion.update(initial, 4, true)
        val start = motion.pose(0).position
        motion.update(wrapped, 4, true)
        assertEquals(start, motion.pose(0).position)
        assertEquals(wrapped.glyphs[0].bounds.size, motion.pose(0).size)
        motion.advanceBy(64f)
        val middle = motion.pose(0)
        val end = wrapped.glyphs[0].bounds.topLeft + wrapped.origin
        assertTrue(middle.position.y in end.y..start.y && middle.position.y != end.y && middle.position.y != start.y)
        motion.advanceBy(150f)
        assertEquals(end, motion.pose(0).position)
    }
}
