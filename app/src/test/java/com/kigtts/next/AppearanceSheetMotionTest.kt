package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Velocity
import com.kigtts.next.ui.settings.AppearanceSheetMotion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) @LooperMode(LooperMode.Mode.PAUSED)
class AppearanceSheetMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val range = mutableFloatStateOf(400f)
    private lateinit var motion: AppearanceSheetMotion
    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = remember { AppearanceSheetMotion(scope, range, 1f, false) {} }
        }
        compose.waitForIdle()
    }

    @Test fun upwardFlingIsNotReversedByTheChildsEmptyRemainder() {
        start()
        compose.runOnIdle {
            motion.drag(-40f)
            runBlocking {
                assertEquals(-800f, motion.onPreFling(Velocity(0f, -800f)).y, .01f)
                motion.onPostFling(Velocity.Zero, Velocity.Zero)
            }
        }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { assertEquals(0f, motion.offset, .1f) }
    }

    @Test fun aNewDragInterruptsSettlingWithoutAnyLaterOffsetWrites() {
        start()
        compose.runOnIdle { motion.expand() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        var dragged = 0f
        compose.runOnIdle {
            assertTrue(motion.offset in 1f..399f)
            motion.interrupt()
            motion.drag(25f)
            dragged = motion.offset
            assertFalse(motion.animating)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertEquals("旧归位动画不能继续改写新拖拽位置", dragged, motion.offset, .01f)
            motion.settle(600f)
        }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { assertEquals(400f, motion.offset, .1f) }
    }

    @Test fun contentGetsRemainingScrollAndOnlyItsUnconsumedDownwardMotionCollapsesTheSheet() {
        start()
        compose.runOnIdle {
            assertEquals(-400f, motion.onPreScroll(Offset(0f, -450f), NestedScrollSource.UserInput).y, .01f)
            assertEquals(Offset.Zero, motion.onPreScroll(Offset(0f, -60f), NestedScrollSource.UserInput))
            assertEquals(Offset.Zero, motion.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput))
            assertEquals(60f, motion.onPostScroll(Offset(0f, 20f), Offset(0f, 60f), NestedScrollSource.UserInput).y, .01f)
            val fraction = motion.fraction
            range.floatValue = 600f
            assertEquals(fraction * 600f, motion.offset, .01f)
        }
    }
}
