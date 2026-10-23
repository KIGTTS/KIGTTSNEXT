package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SidePage
import com.kigtts.next.ui.navigation.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class HomeSideDrawerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var drawer: HomeSideDrawer
    private var opened: SidePage? = null
    private var clicks = 0
    private fun show(childScroll: Boolean = false) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { HomeSideDrawer(scope, { opened = it }, { opened = null }) }
            SideEffect { drawer = state }
            Box(Modifier.size(360.dp, 640.dp).testTag("drawer-root").homeSideDrawer(state) { true }) {
                Box(Modifier.fillMaxSize().then(if (childScroll) Modifier.draggable(rememberDraggableState {}, Orientation.Horizontal)
                    else Modifier.clickable { clicks++ }))
            }
        }
    }
    @Test fun leftDragTracksFingerAndCommitsPolaroidWhileReleaseDoesNotClick() {
        show(); compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawer-root").performTouchInput { down(Offset(width * .7f, height * .4f)); moveBy(Offset(-width * .35f, 0f), 200) }
        compose.runOnIdle { assertEquals(SidePage.CARDS, opened); assertTrue(drawer.visible in .3f.. .4f)
            assertTrue(SideDrawerPose(drawer, true).position in .6f.. .7f) }
        compose.onNodeWithTag("drawer-root").performTouchInput { up() }
        compose.mainClock.autoAdvance = true; compose.waitForIdle()
        assertEquals(SidePage.CARDS, opened); assertFalse(drawer.active); assertEquals(0, clicks)
    }
    @Test fun rightDragOpensSketchAndCancellationReturnsHome() {
        show(); compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawer-root").performTouchInput { down(Offset(width * .3f, height * .4f)); moveBy(Offset(width * .2f, 0f), 200) }
        compose.runOnIdle { assertEquals(SidePage.SKETCH, opened); assertTrue(SideDrawerPose(drawer, true).position < 0f) }
        compose.onNodeWithTag("drawer-root").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true; compose.waitForIdle()
        assertNull(opened); assertFalse(drawer.active)
    }
    @Test fun scrollingChildrenAndSystemBackEdgesKeepTheirGestures() {
        show(childScroll = true)
        compose.onNodeWithTag("drawer-root").performTouchInput { swipe(Offset(width * .6f, height * .4f), Offset(width * .2f, height * .4f), 300) }
        assertNull(opened)
        compose.onNodeWithTag("drawer-root").performTouchInput { swipe(Offset(2f, height * .4f), Offset(width * .5f, height * .4f), 300) }
        assertNull(opened)
    }
    @Test fun rendererQuickSwipeUsesExistingQuickTextsWithoutStartingAnotherSidePage() {
        var quickTexts = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { HomeSideDrawer(scope, { opened = it }, { opened = null }) }
            SideEffect { drawer = state }
            Box(Modifier.size(360.dp, 640.dp).testTag("drawer-root")
                .homeSideDrawer(state, { quickTexts++ }) { true })
        }
        compose.onNodeWithTag("drawer-root").performTouchInput {
            swipe(Offset(width * .7f, height * .4f), Offset(width * .2f, height * .4f), 200)
        }
        compose.runOnIdle { assertEquals(1, quickTexts); assertNull(opened); assertFalse(drawer.active) }
        compose.onNodeWithTag("drawer-root").performTouchInput {
            down(Offset(width * .7f, height * .4f)); moveBy(Offset(-width * .3f, 0f), 150); cancel()
        }
        compose.runOnIdle { assertEquals(1, quickTexts); assertNull(opened) }
    }
    @Test fun fullscreenRendererShortcutDoesNotHijackHomeOrOpenAnotherPageToTheRight() {
        var fullscreen by mutableStateOf(true)
        var quickTexts = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { HomeSideDrawer(scope, { opened = it }, { opened = null }) }
            SideEffect { drawer = state }
            Box(Modifier.size(360.dp, 640.dp).testTag("drawer-root")
                .homeSideDrawer(state,
                    if (fullscreen) ({ fullscreen = false; quickTexts++ }) else null,
                    pagesEnabled = !fullscreen) { true })
        }
        compose.onNodeWithTag("drawer-root").performTouchInput {
            swipe(Offset(width * .2f, height * .4f), Offset(width * .8f, height * .4f), 200)
        }
        compose.runOnIdle { assertNull(opened); assertEquals(0, quickTexts) }
        compose.onNodeWithTag("drawer-root").performTouchInput {
            swipe(Offset(width * .7f, height * .4f), Offset(width * .2f, height * .4f), 200)
        }
        compose.runOnIdle { assertFalse(fullscreen); assertEquals(1, quickTexts); assertNull(opened) }
        compose.onNodeWithTag("drawer-root").performTouchInput {
            swipe(Offset(width * .7f, height * .4f), Offset(width * .2f, height * .4f), 200)
        }
        compose.runOnIdle { assertEquals(SidePage.CARDS, opened); assertEquals(1, quickTexts) }
    }
}
