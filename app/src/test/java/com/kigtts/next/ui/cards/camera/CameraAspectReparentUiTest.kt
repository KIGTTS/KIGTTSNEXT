package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.PolaroidFormat
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CameraAspectReparentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun portraitViewfinderGlidesIntoAndOutOfTheShutterOverlay() {
        checkOverlayTransition(393, 820, .75f, .48f)
    }

    @Test @Config(sdk = [35], qualifiers = "w852dp-h393dp-xxhdpi")
    fun landscapeViewfinderGlidesIntoAndOutOfTheShutterOverlay() {
        checkOverlayTransition(820, 365, 4f / 3f, 2.2f)
    }

    private fun checkOverlayTransition(width: Int, height: Int, standard: Float, wide: Float) {
        var aspect by mutableFloatStateOf(standard)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current
            val owner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context) }
            val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            Box(Modifier.size(width.dp, height.dp)) {
                CameraPageLayout(preview = { modifier, vertical ->
                    CameraPaperPreview(view, PolaroidFormat.MINI, PhotoFilter.ORIGINAL, session, {},
                        modifier, displayReady = true, verticalChrome = vertical, headerOverlay = true, aspectRatio = aspect,
                        fullFrame = aspect == wide, photoSideInset = CAMERA_PREVIEW_SIDE_INSET,
                        chrome = { layout -> Box(Modifier.align(Alignment.TopStart)
                            .cameraChromeBounds(layout.zoom).testTag("zoom-slot")) })
                }, controls = { vertical ->
                    CameraModeControls(CameraMode.PHOTO, {}, true, vertical, false, 0) {
                        CameraActionBar(false, true, true, true, {}, {}, {}, vertical, showCancel = false, gallery = {})
                    }
                }, header = { CameraCornerToolbar(true, false, true, {}, {}, applyInsets = false, showSettings = false) },
                    photoAspect = aspect, fullFrame = aspect == wide)
            }
        } }
        fun photo() = compose.onNodeWithTag("camera-photo-window").getUnclippedBoundsInRoot()
        fun shutter() = compose.onNodeWithTag("camera-shutter").getUnclippedBoundsInRoot()
        fun zoom() = compose.onNodeWithTag("zoom-slot").getUnclippedBoundsInRoot()
        fun back() = compose.onNodeWithTag("camera-cancel").getUnclippedBoundsInRoot()
        fun stage() = compose.onNodeWithTag("camera-stage").getUnclippedBoundsInRoot()
        val start = photo()
        val dock = shutter()
        val toolbar = back()
        assertTrue(start.top >= toolbar.bottom)
        val zoomStart = zoom()
        compose.mainClock.autoAdvance = false
        fun moveTo(ratio: Float, frames: Int) {
            var previous = photo()
            compose.runOnIdle { aspect = ratio }
            repeat(frames) {
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                val next = photo()
                assertEquals(toolbar, back())
                assertTrue(next.left >= stage().left + 11.dp && next.right <= stage().right - 11.dp)
                val step = maxOf(abs((next.left - previous.left).value), abs((next.top - previous.top).value),
                    abs((next.right - previous.right).value), abs((next.bottom - previous.bottom).value))
                assertTrue("Viewfinder jumped ${step}dp between frames: $previous -> $next", step < 42f)
                assertEquals(dock, shutter())
                assertEquals(zoomStart.left.value, zoom().left.value, 1f)
                assertEquals(zoomStart.top.value, zoom().top.value, 1f)
                previous = next
            }
        }
        moveTo(wide, 28)
        val expanded = photo()
        assertEquals(4f, (expanded.top - stage().top).value, 1f)
        assertTrue("Full frame must reach behind the toolbar", expanded.top < toolbar.top)
        assertEquals(wide, (expanded.right - expanded.left) / (expanded.bottom - expanded.top), .01f)
        assertTrue("Wide viewfinder should overlap the fixed dock", if (width > height) expanded.right > dock.left else expanded.bottom > dock.top)
        moveTo(standard, 7)
        moveTo(wide, 5)
        moveTo(standard, 28)
        assertEquals(start, photo())
    }

    @Test fun fullFrameDropsTheHeaderReserveEvenWhenTheNumericRatioIsUnchanged() {
        var fullFrame by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current
            val owner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context) }
            val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            CameraPaperPreview(view, PolaroidFormat.MINI, PhotoFilter.ORIGINAL, session, {},
                Modifier.size(360.dp, 540.dp), displayReady = true, headerOverlay = true,
                aspectRatio = .75f, fullFrame = fullFrame, photoSideInset = CAMERA_PREVIEW_SIDE_INSET)
        } }
        fun photo() = compose.onNodeWithTag("camera-photo-window").getUnclippedBoundsInRoot()
        val original = photo()
        assertEquals(CAMERA_HEADER_CLEARANCE, original.top.value, 1f)
        assertEquals(CAMERA_PREVIEW_SIDE_INSET, original.left.value, 1f)
        compose.runOnIdle { fullFrame = true }
        assertEquals(0f, photo().top.value, 1f)
        assertEquals((original.right - original.left).value, (photo().right - photo().left).value, 1f)
        compose.runOnIdle { fullFrame = false }
        assertEquals(original, photo())
    }

    @Test fun repeatedOverlayChangesKeepOnePreviewAndOneControlComposition() {
        var aspect by mutableFloatStateOf(.75f)
        var previewCreates = 0; var controlCreates = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(393.dp, 820.dp)) {
                CameraPageLayout(preview = { modifier, _ ->
                    remember { previewCreates++ }
                    Box(modifier.testTag("retained-preview"))
                }, controls = { vertical ->
                    remember { controlCreates++ }
                    CameraActionBar(false, true, true, true, {}, {}, {}, vertical, showCancel = false, gallery = {})
                }, photoAspect = aspect)
            }
        } }
        compose.mainClock.autoAdvance = false
        repeat(48) { index ->
            compose.runOnIdle { aspect = if (index % 2 == 0) .48f else .75f }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onNodeWithTag("retained-preview").assertIsDisplayed()
            compose.onNodeWithTag("camera-shutter").assertIsDisplayed()
        }
        assertEquals(1, previewCreates); assertEquals(1, controlCreates)
    }
}
