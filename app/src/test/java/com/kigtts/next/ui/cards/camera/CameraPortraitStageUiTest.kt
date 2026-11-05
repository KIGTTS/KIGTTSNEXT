package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.takahirom.roborazzi.captureRoboImage
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
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CameraPortraitStageUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ratioMorphKeepsTheToolbarShutterAndZoomFixedInTallAndShortPortraitWindows() {
        var format by mutableStateOf(PolaroidFormat.MINI)
        var width by mutableStateOf(393.dp); var height by mutableStateOf(820.dp)
        var retained: PreviewView? = null; var backs = 0; var settings = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context).also { retained = it } }
            val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            Box(Modifier.size(width, height)) {
                CameraPageLayout(preview = { modifier, vertical ->
                    CameraPaperPreview(view, format, PhotoFilter.ORIGINAL, session, {}, modifier,
                        displayReady = true, verticalChrome = vertical, headerOverlay = true, chrome = { layout ->
                            CameraOverlayControls(format, listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE), PhotoFilter.ORIGINAL,
                                true, vertical, false, { format = if (format == PolaroidFormat.MINI) PolaroidFormat.SQUARE else PolaroidFormat.MINI },
                                {}, {}, {}, layout = layout, showSettings = false)
                            CameraZoomRail(1f, 1f, 4f, true, {}, vertical, Modifier.align(Alignment.TopStart).cameraChromeBounds(layout.zoom))
                        })
                }, controls = { vertical -> CameraActionBar(false, true, true, true, {}, {}, {}, vertical,
                    showCancel = false, gallery = {}) },
                    header = { CameraCornerToolbar(true, false, true, { backs++ }, { settings++ }, applyInsets = false) },
                    photoAspect = format.geometry.photoAspectRatio.toFloat())
            }
        } }
        val originalView = retained
        fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        fun switchAndCheck() {
            val stage = bounds("camera-stage")
            val back = bounds("camera-cancel"); val settingsBounds = bounds("camera-settings")
            val shutter = bounds("camera-shutter")
            val zoom = bounds("camera-zoom-stop/1")
            val photoTop = bounds("camera-photo-window").top.value
            assertEquals(stage.top.value + 8f, back.top.value, 1f)
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { format = if (format == PolaroidFormat.MINI) PolaroidFormat.SQUARE else PolaroidFormat.MINI }
            repeat(24) {
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                assertEquals(stage, bounds("camera-stage"))
                assertEquals(back, bounds("camera-cancel")); assertEquals(settingsBounds, bounds("camera-settings"))
                assertEquals(shutter, bounds("camera-shutter"))
                val photo = bounds("camera-photo-window")
                assertEquals(photoTop, photo.top.value, 2f)
                assertEquals(zoom, bounds("camera-zoom-stop/1"))
                assertEquals((stage.left + stage.right).value / 2f, (photo.left + photo.right).value / 2f, 1f)
                assertTrue(photo.bottom.value <= shutter.top.value + 1f)
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val photo = bounds("camera-photo-window")
            assertEquals(format.geometry.photoAspectRatio, ((photo.right - photo.left) / (photo.bottom - photo.top)).toDouble(), .01)
            compose.onNodeWithTag("camera-zoom-stop/1").assertIsDisplayed()
            compose.onNodeWithTag("camera-cancel").performClick()
            compose.onNodeWithTag("camera-settings").performClick()
            assertSame(originalView, retained)
        }
        switchAndCheck()
        compose.onRoot().captureRoboImage("build/previews/camera-square-centred-portrait.png")
        switchAndCheck()
        compose.runOnIdle { width = 360.dp; height = 650.dp }
        switchAndCheck(); switchAndCheck()
        assertEquals(4, backs); assertEquals(4, settings)
    }
}
