package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
class CameraPreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun landscapeKeepsTheControlRailNarrowAndCyclesOneRatioIcon() {
        var format by mutableStateOf(PolaroidFormat.WIDE)
        val formats = listOf(PolaroidFormat.WIDE, PolaroidFormat.SQUARE)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraPageLayout({ modifier -> Box(modifier.testTag("test-preview").background(MaterialTheme.colorScheme.surface)) },
                { CameraFilters(PhotoFilter.ORIGINAL, true, false, {}) }, { vertical ->
                    CameraActionBar(false, true, true, true, format, formats, {}, {}, {},
                        { format = nextCameraFormat(formats, format) }, vertical)
                })
        } }
        val page = compose.onNodeWithTag("polaroid-camera").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("camera-side-controls").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("test-preview").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.width < page.width * .2f)
        assertTrue(preview.right <= controls.left)
        compose.onAllNodesWithTag("camera-format").assertCountEquals(1)
        compose.onNodeWithTag("camera-format").assertContentDescriptionContains("画幅：矩形，点按切换").performClick()
        assertEquals(PolaroidFormat.SQUARE, format)
        compose.onNodeWithTag("camera-format").assertContentDescriptionContains("画幅：方形，点按切换")
    }
    @Test fun exposureHandleStaysInsideThePhotoAndSupportsRealDraggingAndAccessibility() {
        var index by mutableIntStateOf(0)
        var focused by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val density = LocalDensity.current
            Box(Modifier.size(220.dp, 180.dp).testTag("test-photo")) {
                CameraFocusOverlay(if (focused) CameraFocusTarget(with(density) { 214.dp.toPx() }, with(density) { 174.dp.toPx() }) else null,
                    index, -2..2, true, { index = it })
            }
        } }
        val photo = compose.onNodeWithTag("test-photo").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("camera-exposure").fetchSemanticsNode().boundsInRoot
        assertTrue(handle.left >= photo.left && handle.right <= photo.right && handle.top >= photo.top && handle.bottom <= photo.bottom)
        compose.onNodeWithTag("camera-exposure").performTouchInput { swipe(center, topCenter + Offset(0f, 8f), 300) }
        assertEquals(2, index)
        compose.onNodeWithTag("camera-exposure").performSemanticsAction(SemanticsActions.SetProgress) { it(-2f) }
        assertEquals(-2, index)
        compose.runOnIdle { focused = false }
        compose.waitForIdle()
        compose.onNodeWithTag("camera-exposure").assertDoesNotExist()
    }
    @Test fun ratioWindowAnimatesWithoutWhitePaperAndReportsOnlyTheFinalSettledSize() {
        var format by mutableStateOf(PolaroidFormat.MINI)
        val phases = mutableListOf<Boolean>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
            val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            CameraPaperPreview(view, format, PhotoFilter.ORIGINAL, session, {}, Modifier.size(340.dp, 320.dp),
                displayReady = true, morphChanged = { phases += it })
        } }
        val initial = compose.onNodeWithTag("camera-photo-window").assertExists().fetchSemanticsNode().boundsInRoot
        assertEquals("initial=$initial", PolaroidFormat.MINI.geometry.photoAspectRatio,
            (initial.width / initial.height).toDouble(), .01)
        compose.onNodeWithTag("camera-paper").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { format = PolaroidFormat.SQUARE; Snapshot.sendApplyNotifications() }
        fun step() { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        repeat(8) { if (!phases.contains(true)) step() }
        compose.runOnIdle { assertTrue("The format change must start its animation before sampling", phases.contains(true)) }
        repeat(7) { step() }
        val mid = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        assertTrue("mid=$mid ratio=" + mid.width / mid.height + " phases=$phases",
            mid.width / mid.height > PolaroidFormat.MINI.geometry.photoAspectRatio && mid.width / mid.height < 1f)
        repeat(25) { step() }
        val settled = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        assertEquals(1.0, (settled.width / settled.height).toDouble(), .01)
        assertTrue(phases.contains(true)); assertEquals(false, phases.last())
    }
    @Test fun automaticClockStartsAndSettlesTheRealPreviewMorph() {
        var format by mutableStateOf(PolaroidFormat.MINI)
        val phases = mutableListOf<Boolean>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
            val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            CameraPaperPreview(view, format, PhotoFilter.ORIGINAL, session, {}, Modifier.size(340.dp, 320.dp),
                displayReady = true, morphChanged = { phases += it })
        } }
        val initial = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        assertEquals(PolaroidFormat.MINI.geometry.photoAspectRatio, (initial.width / initial.height).toDouble(), .01)
        compose.runOnIdle { format = PolaroidFormat.SQUARE }
        compose.waitForIdle()
        val final = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        assertTrue("phases=" + phases, phases.contains(true)); assertEquals(false, phases.last())
        assertEquals(1.0, (final.width / final.height).toDouble(), .01)
    }
}
