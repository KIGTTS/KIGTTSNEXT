package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.PolaroidFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CameraFilterZoomUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var ratio = 1f
    private fun check(width: Int, height: Int, vertical: Boolean) {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            var expanded by remember { mutableStateOf(false) }
            var filter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
            val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context) }
            val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            CameraPaperPreview(view, PolaroidFormat.SQUARE, filter, session, {}, Modifier.size(width.dp, height.dp),
                displayReady = true, verticalChrome = vertical, chrome = { layout ->
                    CameraOverlayControls(PolaroidFormat.SQUARE, listOf(PolaroidFormat.SQUARE), filter, true, vertical, expanded,
                        {}, {}, { expanded = !expanded }, { filter = it }, layout = layout)
                    CameraZoomRail(ratio, .6f, 8f, true, { ratio = it }, vertical,
                        Modifier.matchParentSize(), bounds = layout.zoom)
                })
        } }
        val paper = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        val rail = compose.onNodeWithTag("camera-zoom-rail").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("camera-filter-toggle").performClick()
        compose.onNodeWithTag("camera-filter-toggle").assertIsDisplayed()
        compose.onNodeWithTag("camera-zoom-stop/2").assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(ratio > 1f)
        val filters = compose.onNodeWithTag("camera-filters").fetchSemanticsNode().boundsInRoot
        val slider = compose.onNodeWithTag("camera-filter-strength").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("camera-filter-collapse").fetchSemanticsNode().boundsInRoot
        if (vertical) { assertTrue("$filters / $rail", filters.right <= rail.left); assertTrue(close.bottom <= slider.top) }
        else { assertTrue("$filters / $rail", filters.bottom <= rail.top); assertTrue("Close $close must follow slider $slider", close.left >= slider.right) }
        assertEquals(paper, compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot)
        assertEquals(rail, compose.onNodeWithTag("camera-zoom-rail").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("camera-filter-list").performScrollToNode(hasTestTag("camera-filter/BLACK_GOLD"))
        compose.onNodeWithTag("camera-filter/BLACK_GOLD").performClick().assertIsSelected()
        compose.onNodeWithTag("camera-filter-collapse").performTouchInput { click() }
        compose.onNodeWithTag("camera-filters").assertDoesNotExist()
        compose.onNodeWithTag("camera-filter-toggle").assertIsDisplayed()
    }
    @Test fun compactPortraitKeepsBothRails() = check(340, 380, false)
    @Test fun roomyPortraitKeepsBothRails() = check(360, 640, false)
    @Test fun compactLandscapeKeepsBothRails() = check(340, 300, true)
    @Test fun roomyLandscapeKeepsBothRails() = check(700, 300, true)
}
