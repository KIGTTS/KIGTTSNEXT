package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
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
class CameraFullscreenChromeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theFloatingHeaderHasNoTitleBackgroundOrReservedContentHeight() {
        val previewColor = Color(0xFF123456)
        var safeVertical = 0; var reservedControls = 0f
        var background = Color.Unspecified
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            background = MaterialTheme.colorScheme.background
            val density = LocalDensity.current
            safeVertical = WindowInsets.safeDrawing.getTop(density) + WindowInsets.safeDrawing.getBottom(density)
            reservedControls = with(density) { (92.dp + 8.dp).toPx() }
            Box(Modifier.size(360.dp, 640.dp).testTag("test-camera-fullscreen")) {
                CameraPageLayout({ modifier, _ -> Box(modifier.background(previewColor).testTag("test-fullscreen-preview")) },
                    { vertical -> CameraActionBar(false, true, true, true, {}, {}, {}, vertical, showCancel = false, gallery = {}) },
                    header = { CameraCornerToolbar(true, false, true, {}, {}, applyInsets = false) {
                        CameraMotionButton(false, true, true, false, true, {})
                    } })
            }
        } }
        val root = compose.onNodeWithTag("test-camera-fullscreen").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("test-fullscreen-preview").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithTag("camera-cancel").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("camera-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(root.height - safeVertical - reservedControls, preview.height, 2f)
        assertTrue(back.center.x < root.center.x && settings.center.x > root.center.x)
        compose.onAllNodesWithTag("camera-cancel").assertCountEquals(1)
        compose.onAllNodesWithTag("camera-settings").assertCountEquals(1)
        compose.onAllNodesWithText("拍摄").assertCountEquals(0)
        val output = File("build/previews/camera-transparent-corner-toolbar.png").apply { parentFile.mkdirs() }
        compose.onNodeWithTag("test-camera-fullscreen").captureRoboImage(output.absolutePath)
        val pixels = output.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            val y = ((back.center.y - root.top) / root.height * pixels.height).toInt().coerceIn(0, pixels.height - 1)
            val sampled = Color(pixels.getPixel(pixels.width / 2, y))
            assertEquals(previewColor.red, sampled.red, .01f)
            assertEquals(previewColor.green, sampled.green, .01f)
            assertEquals(previewColor.blue, sampled.blue, .01f)
            val dockY = ((preview.bottom - root.top) / root.height * pixels.height).toInt() + 8
            val outside = Color(pixels.getPixel(4, dockY))
            assertEquals(background.red, outside.red, .01f)
            assertEquals(background.green, outside.green, .01f)
            assertEquals(background.blue, outside.blue, .01f)
        } finally { pixels.recycle() }
    }

    @Test fun photoAndProKeepTheSameShutterAnchorInPortrait() = verifySharedShutterAnchor(360, 760)

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun photoAndProKeepTheSameShutterAnchorInLandscape() = verifySharedShutterAnchor(760, 360)

    private fun verifySharedShutterAnchor(width: Int, height: Int) {
        var pro by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(width.dp, height.dp)) {
                CameraPageLayout(preview = { modifier, _ -> Box(modifier) },
                    controls = { vertical ->
                        CameraModeControls(if (pro) CameraMode.PRO else CameraMode.PHOTO, {}, true, vertical, false, 0) {
                            CameraActionBar(false, true, true, true, {}, {}, {}, vertical, showCancel = false, gallery = {})
                        }
                    }, photoAspect = if (width > height) 4f / 3 else 3f / 4,
                    captureBaseline = true, professional = pro)
            }
        } }
        val shutter = compose.onNodeWithTag("camera-shutter")
        val photo = shutter.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { pro = true }
        val professional = shutter.fetchSemanticsNode().boundsInRoot
        assertEquals(photo, professional)
    }

    @Test fun cornerActionsUseCurrentCallbacksAndSavingOnlyLocksTheNecessaryActions() {
        var busy by mutableStateOf(false); var countdown by mutableStateOf(false)
        var active by mutableStateOf(true)
        var backs = 0; var settings = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraCornerToolbar(active, busy, !busy && !countdown, { backs++ }, { settings++ })
        } }
        compose.onNodeWithTag("camera-cancel").performClick(); assertEquals(1, backs)
        compose.onNodeWithTag("camera-settings").performClick(); assertEquals(1, settings)
        compose.runOnIdle { countdown = true }
        compose.onNodeWithTag("camera-cancel").assertIsEnabled()
        compose.onNodeWithTag("camera-settings").assertIsNotEnabled()
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("camera-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("camera-header-loading").assertDoesNotExist()
        compose.runOnIdle { busy = false; countdown = false }
        compose.onNodeWithTag("camera-settings").performClick(); assertEquals(2, settings)
        compose.runOnIdle { active = false }
        compose.onNodeWithTag("camera-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("camera-settings").assertIsNotEnabled()
    }

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun landscapeCameraActionsStayInTheViewfinderAwayFromTheShutterAndGallery() {
        var safeVertical = 0; var previewPadding = 0f
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val density = LocalDensity.current
            safeVertical = WindowInsets.safeDrawing.getTop(density) + WindowInsets.safeDrawing.getBottom(density)
            previewPadding = with(density) { 8.dp.toPx() }
            Box(Modifier.size(800.dp, 300.dp).testTag("test-camera-fullscreen")) {
                CameraPageLayout({ modifier, _ -> Box(modifier.testTag("test-fullscreen-preview")) },
                    { vertical -> CameraActionBar(false, true, true, true, {}, {}, {}, vertical, showCancel = false, gallery = {}) },
                    header = { CameraCornerToolbar(true, false, true, {}, {}, applyInsets = false) {
                        CameraMotionButton(false, true, true, false, true, {})
                    } })
            }
        } }
        val root = compose.onNodeWithTag("test-camera-fullscreen").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("test-fullscreen-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(root.height - safeVertical - previewPadding, preview.height, 2f)
        compose.onNodeWithTag("camera-side-controls").assertExists()
        val controls = compose.onNodeWithTag("camera-side-controls").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag("camera-shutter").fetchSemanticsNode().boundsInRoot
        val flip = compose.onNodeWithTag("camera-flip").fetchSemanticsNode().boundsInRoot
        val gallery = compose.onNodeWithTag("camera-gallery").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait right/left actions rotate to top/bottom", flip.bottom < shutter.top && shutter.bottom < gallery.top)
        listOf("camera-settings", "camera-motion").forEach { tag ->
            val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag belongs inside the viewfinder", action.right <= preview.right)
            assertFalse("$tag must not overlap the shutter rail", action.overlaps(controls))
            compose.onNodeWithTag(tag).performTouchInput { click(center) }
        }
        compose.onNodeWithTag("camera-cancel").assertIsDisplayed()
        compose.onNodeWithTag("camera-settings").assertIsDisplayed()
        compose.onAllNodesWithText("拍摄").assertCountEquals(0)
    }
}
