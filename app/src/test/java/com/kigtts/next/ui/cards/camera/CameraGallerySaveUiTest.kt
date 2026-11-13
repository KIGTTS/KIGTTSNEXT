package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CameraGallerySaveUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun saveRingDoesNotMoveOrInterceptCameraControls() {
        var saving by mutableStateOf(false)
        var gallery = 0
        var captures = 0
        var flips = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraActionBar(false, true, true, true, {}, { captures++ }, { flips++ }, false,
                showCancel = false, gallery = { gallery++ }, gallerySaving = saving)
        } }
        val button = compose.onNodeWithTag("camera-gallery")
        val bounds = button.getUnclippedBoundsInRoot()
        compose.onNodeWithTag("camera-gallery-saving").assertDoesNotExist()
        compose.runOnIdle { saving = true }
        val ring = compose.onNodeWithTag("camera-gallery-saving").assertIsDisplayed()
        assertEquals(bounds, button.getUnclippedBoundsInRoot())
        val progress = ring.getUnclippedBoundsInRoot()
        assertEquals((bounds.left + bounds.right).value, (progress.left + progress.right).value, 1f)
        assertEquals((bounds.top + bounds.bottom).value, (progress.top + progress.bottom).value, 1f)
        button.performTouchInput { click() }
        compose.onNodeWithTag("camera-shutter").performClick()
        compose.onNodeWithTag("camera-flip").performClick()
        assertEquals(listOf(1, 1, 1), listOf(gallery, captures, flips))
        compose.runOnIdle { saving = false }
        ring.assertDoesNotExist()
    }

    @Test fun liveCaptureCanLockOnlyTheShutterBeforeBackgroundSaving() {
        var busy by mutableStateOf(true)
        var gallery = 0
        var flips = 0
        var captures = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraActionBar(busy, true, true, true, {}, { captures++ }, { flips++ }, false,
                showCancel = false, gallery = { gallery++ }, configurationLocked = false, gallerySaving = !busy)
        } }
        compose.onNodeWithTag("camera-shutter").assertIsNotEnabled()
        compose.onNodeWithTag("camera-gallery").assertIsEnabled().performClick()
        compose.onNodeWithTag("camera-flip").assertIsEnabled().performClick()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("camera-gallery-saving").assertIsDisplayed()
        compose.onNodeWithTag("camera-shutter").assertIsEnabled().performClick()
        assertEquals(listOf(1, 1, 1), listOf(gallery, flips, captures))
    }
}
