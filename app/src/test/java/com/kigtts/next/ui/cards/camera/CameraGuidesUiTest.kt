package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.theme.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CameraGuidesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun turningGuidesOnChangesOnlyTheOverlayAndLeavesThePhotoBoundsStable() {
        var grid by mutableStateOf(CameraGrid.OFF)
        compose.setContent { Box(Modifier.size(240.dp, 300.dp).background(Color.Black).testTag("photo")) {
            CameraCompositionGuides(grid, false, Surface.ROTATION_0, false, Modifier.matchParentSize())
        } }
        val photo = compose.onNodeWithTag("photo")
        val bounds = photo.fetchSemanticsNode().boundsInRoot
        fun guideRed(): Float {
            val output = File("build/previews/camera-guides.png"); requireNotNull(output.parentFile).mkdirs()
            photo.captureRoboImage(output.absolutePath)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            return try { android.graphics.Color.red(bitmap.getPixel(bitmap.width / 3, bitmap.height / 2)) / 255f }
            finally { bitmap.recycle() }
        }
        assertTrue(guideRed() < .01f)
        compose.runOnIdle { grid = CameraGrid.THIRDS }
        assertEquals(bounds, photo.fetchSemanticsNode().boundsInRoot)
        assertTrue(guideRed() > .2f)
        compose.runOnIdle { grid = CameraGrid.OFF }
        assertTrue(guideRed() < .01f)
    }
    @Test fun settingsUseNativeConnectedChoicesAndRespectUnavailableFlash() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("camera-ui-test", 0)
        prefs.edit().clear().commit()
        val options = CameraOptions(prefs).apply { showSettings() }
        compose.setContent { KigttsTheme(ThemeSettings()) { CameraSettingsPanel(options, flashAvailable = false) } }
        compose.onNodeWithText("当前镜头不支持闪光灯").assertExists()
        compose.onNodeWithText("自动").assertIsNotEnabled()
        compose.onNodeWithText("方格").performClick()
        assertEquals(CameraGrid.SQUARE, options.grid)
        compose.onNodeWithText("10 秒").performScrollTo().performClick()
        assertEquals(10, options.timerSeconds)
        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()
        assertFalse(options.settingsVisible)
        compose.onNodeWithText("相机设置").assertDoesNotExist()
    }
}
