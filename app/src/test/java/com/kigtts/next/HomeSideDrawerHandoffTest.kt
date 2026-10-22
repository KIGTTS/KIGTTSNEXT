package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SidePage
import com.kigtts.next.ui.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class HomeSideDrawerHandoffTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun cancelSketch(cancelEvent: Boolean) {
        var open by mutableStateOf(false)
        var retained by mutableStateOf(false)
        lateinit var drawer: HomeSideDrawer
        lateinit var pose: SideDrawerPose
        val entry = PageEntry(PageScene.Side(SidePage.SKETCH), 1)
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { HomeSideDrawer(scope, { open = true; retained = true }, { open = false }) }
            val sidePose = remember(state) { SideDrawerPose(state, true) }
            SideEffect { drawer = state; pose = sidePose }
            Box(Modifier.size(360.dp, 640.dp).clipToBounds().background(Color.Green)
                .testTag("drawer-handoff").homeSideDrawer(state) { !open }) {
                if (retained) PageMotion(entry, if (open) PageRole.FOREGROUND else PageRole.EXITING,
                    false, 0f, 0, 0f, { retained = false }, drawerPose = if (state.active) sidePose else null) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawer-handoff").performTouchInput {
            down(Offset(width * .3f, height * .4f)); moveBy(Offset(width * .16f, 0f), 400)
        }
        compose.waitForIdle()
        assertTrue(drawer.active)
        compose.onNodeWithTag("drawer-handoff").performTouchInput {
            advanceEventTime(200)
            if (cancelEvent) cancel() else up()
        }
        var lastRight = Float.MAX_VALUE
        repeat(36) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            if (retained) {
                val right = compose.onNodeWithTag("page-layer/" + entry.identity).getUnclippedBoundsInRoot().right.value
                assertTrue("Returning sketch must not re-enter as a white strip: $right after $lastRight", right <= lastRight + .1f)
                if (!drawer.active) assertTrue("Completed gesture must stay offscreen", right <= .1f)
                lastRight = right
            }
        }
        assertFalse(open); assertFalse(retained); assertFalse(drawer.active)
        assertEquals(-1f, pose.position, 0f)
        compose.mainClock.autoAdvance = true
    }
    @Test fun shortReleasedSwipeReturnsWithoutASecondExitAnimation() = cancelSketch(false)
    @Test fun cancelledPointerKeepsItsLeftHandOffscreenPose() = cancelSketch(true)
}
