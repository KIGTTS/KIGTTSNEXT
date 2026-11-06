package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CameraHomeGestureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var opens = 0
    private var distance = 0f
    private var selectedPage = 0

    private fun show(child: String) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            var camera by remember { mutableStateOf<String?>(null) }
            val pull = remember { CameraPullState(scope,
                { opens++; "camera".also { camera = it } }, { camera = null }, { camera }) }
            val exclusions = remember { HomeSwipeExclusions() }
            CompositionLocalProvider(LocalHomeSwipeExclusions provides exclusions) {
                Box(Modifier.size(360.dp, 640.dp).testTag("root").cameraPullNavigation(pull) {
                    if (camera == null) CameraPullOrigin(CameraPullMode.ENTER, "home", horizontal = true) else null
                }) {
                    when (child) {
                        "pager" -> {
                            val pager = rememberPagerState { 3 }
                            val current = pager.currentPage
                            SideEffect { selectedPage = current }
                            HorizontalPager(pager, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                        }
                        "slider" -> Box(Modifier.fillMaxSize().draggable(
                            rememberDraggableState { distance += it }, Orientation.Horizontal))
                        "subtitle" -> Box(Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures { change, delta -> change.consume(); distance += delta.x }
                        })
                        "excluded" -> Box(Modifier.fillMaxSize().excludeHomeSideSwipes())
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun swipe() {
        compose.onNodeWithTag("root").performTouchInput {
            swipe(Offset(width * .8f, height * .5f), Offset(width * .2f, height * .5f), 300)
        }
        compose.waitForIdle()
    }

    @Test fun quickTextPagerKeepsTheSwipeWithoutOpeningCamera() {
        show("pager"); swipe(); assertEquals(0, opens); assertEquals(1, selectedPage)
    }
    @Test fun landscapeSliderKeepsItsDragWithoutOpeningCamera() {
        show("slider"); swipe(); assertEquals(0, opens); assertTrue(distance < -100f)
    }
    @Test fun subtitleDrawingKeepsItsStrokeWithoutOpeningCamera() {
        show("subtitle"); swipe(); assertEquals(0, opens); assertTrue(distance < -100f)
    }
    @Test fun existingSharedExclusionBlocksCameraEvenBeforeChildConsumes() {
        show("excluded"); swipe(); assertEquals(0, opens)
    }
    @Test fun unclaimedHomeSwipeStillOpensCamera() {
        show("blank"); swipe(); assertEquals(1, opens)
    }
}
