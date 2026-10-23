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
    @Test fun reflowExitsTheViewportBeforeEnteringAndContinuousZoomDoesNotRestartIt() {
        val motion = SubtitleGlyphMotion()
        val initial = frame("甲乙丙丁", width = 200)
        val wrapped = frame("甲乙丙丁", width = 100)
        motion.update(initial, 4, true)
        motion.update(wrapped, 4, true)
        val index = initial.glyphs.indices.first { initial.glyphs[it].line != wrapped.glyphs[it].line }
        assertEquals(initial.glyphs[index].bounds.topLeft + initial.origin, motion.outgoing(index)!!.position)
        motion.advanceBy(100f)
        assertTrue(motion.outgoing(index)!!.position.x > wrapped.viewport.width)
        assertEquals(0f, motion.pose(index).alpha, 0f)
        motion.advanceBy(40f)
        assertNull(motion.outgoing(index))
        assertTrue(motion.pose(index).position.x < 0f)
        assertTrue(motion.pose(index).alpha > 0f)
        repeat(7) {
            motion.update(frame("甲乙丙丁", font = 39, width = 99 - it), 4, true)
            motion.advanceBy(16f)
        }
        assertFalse("Continuous layout updates must not starve the line transition", motion.running)
        assertEquals(1f, motion.pose(index).alpha, 0f)
    }
    @Test fun reverseReflowLeavesLeftAndReentersAtTheRightAtTheNewLineHeight() {
        val start = SubtitleGlyphPose(Offset(20f, 180f), Size(40f, 50f))
        val hop = SubtitleLineHop(start, -1f, 0f)
        val target = start.copy(position = Offset(250f, 90f))
        assertTrue(hop.outgoing(100f, 320f)!!.position.x < -start.size.width)
        assertEquals(0f, hop.incoming(100f, 320f, target).alpha, 0f)
        val entering = hop.incoming(140f, 320f, target)
        assertTrue(entering.position.x > 320f)
        assertEquals(90f, entering.position.y, 0f)
        assertEquals(target, hop.incoming(240f, 320f, target))
    }
}
