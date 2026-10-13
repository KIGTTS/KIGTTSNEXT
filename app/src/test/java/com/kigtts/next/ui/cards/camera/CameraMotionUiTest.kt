package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
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
class CameraMotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun preparingMotionCanBeTurnedOffAndALimitedCameraStillOffersTheOrdinaryShutter() {
        var requested by mutableStateOf(false); var preparing by mutableStateOf(false); var saves = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { Column {
            CameraMotionButton(requested, false, false, preparing, true) { requested = it }
            CameraActionBar(false, true, true, true, {}, { saves++ }, {}, false, gallery = {})
        } } }
        compose.onNodeWithTag("camera-motion").assertIsNotSelected().performClick()
        compose.runOnIdle { preparing = true }
        compose.onNodeWithTag("camera-motion").assertIsSelected().assertIsEnabled().performClick()
        compose.onNodeWithTag("camera-motion").assertIsNotSelected()
        compose.runOnIdle { preparing = false }
        compose.onNodeWithTag("camera-shutter").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test fun cameraGalleryIsIndependentFromBackAndIsLockedDuringPhotoDeliveryAndCountdown() {
        var locked by mutableStateOf(false); var galleries = 0; var backs = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(340.dp, 160.dp)) {
                CameraActionBar(false, true, true, true, { backs++ }, {}, {}, false,
                    locked = locked, showCancel = false, gallery = { galleries++ })
            }
        } }
        compose.onNodeWithTag("camera-gallery").assertIsEnabled().performClick()
        assertEquals(1, galleries); assertEquals(0, backs)
        compose.onNodeWithTag("camera-cancel").assertDoesNotExist()
        compose.runOnIdle { locked = true }
        compose.onNodeWithTag("camera-gallery").assertIsNotEnabled()
        compose.runOnIdle { locked = false }
        compose.onNodeWithTag("camera-gallery").assertIsEnabled().performClick()
        assertEquals(2, galleries); assertEquals(0, backs)
    }

    @Test fun motionPersistsAndTemporarilyUsesStandardWithoutErasingTheOemPhotoPreference() {
        val preferences = compose.activity.getSharedPreferences("motion-camera-ui", 0).apply { edit().clear().commit() }
        val options = CameraOptions(preferences)
        options.setProcessing(CameraProcessing.HDR); options.setMotion(true)
        assertEquals(CameraProcessing.STANDARD, options.processing)
        val reopened = CameraOptions(preferences)
        assertTrue(reopened.motion); assertEquals(CameraProcessing.STANDARD, reopened.processing)
        reopened.setMotion(false)
        assertEquals(CameraProcessing.HDR, reopened.processing)
        assertFalse(CameraOptions(preferences).motion)
    }
}
