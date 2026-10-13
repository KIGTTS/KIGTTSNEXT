package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CameraInteractionStateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun retainedNativePreviewRefreshesEnabledCallbacksAndHardwareFlashAfterStartup() {
        var active by mutableStateOf(false); var busy by mutableStateOf(false)
        var hasFlash by mutableStateOf(false); var flash by mutableStateOf(CameraFlash.OFF)
        var clicks = 0; var factories = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context) }; val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            val enabled = active && !busy
            val preview = rememberCameraPreviewContent(view, session) { modifier, vertical ->
                Box(modifier) {
                    AndroidView({ factories++; view }, Modifier.fillMaxSize())
                    CameraOverlayControls(PolaroidFormat.MINI, listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE),
                        PhotoFilter.ORIGINAL, enabled, vertical, false, {}, { clicks++ }, {}, {}, flash, hasFlash,
                        { flash = CameraFlash.AUTO })
                }
            }
            preview(Modifier.size(340.dp, 320.dp), false)
        } }
        compose.onNodeWithTag("camera-settings").assertIsNotEnabled()
        compose.runOnIdle { active = true; hasFlash = true }
        compose.onNodeWithTag("camera-settings").assertIsEnabled().performClick()
        assertEquals(1, clicks)
        compose.onNodeWithTag("camera-flash").assertIsEnabled().performClick()
            .assertContentDescriptionContains("闪光灯：自动，点按切换")
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("camera-settings").assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("camera-settings").assertIsEnabled()
        assertEquals("Control changes must retain the same AndroidView", 1, factories)
    }

    @Test fun movingThePreviewPreservesItsNativeViewAndRefreshesFormatListAndRotationInputs() {
        var vertical by mutableStateOf(false)
        var factories = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
            val view = remember { PreviewView(context) }; val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
            val formats = if (vertical) listOf(PolaroidFormat.MINI, PolaroidFormat.WIDE, PolaroidFormat.SQUARE)
                else listOf(PolaroidFormat.WIDE, PolaroidFormat.SQUARE)
            val rotation = if (vertical) 90 else 0
            val preview = rememberCameraPreviewContent(view, session) { modifier, side ->
                Box(modifier) {
                    AndroidView({ factories++; view }, Modifier.fillMaxSize())
                    CameraOverlayControls(PolaroidFormat.WIDE, formats, PhotoFilter.ORIGINAL, true, side, false,
                        {}, {}, {}, {})
                    Text("$rotation", Modifier.testTag("test-preview-rotation"))
                }
            }
            if (vertical) Row { preview(Modifier.size(320.dp, 340.dp), true) }
            else Column { preview(Modifier.size(340.dp, 320.dp), false) }
        } }
        compose.onNodeWithTag("camera-format").assertContentDescriptionContains("画幅：矩形，点按切换")
        compose.runOnIdle { vertical = true }
        compose.onNodeWithTag("camera-format").assertContentDescriptionContains("画幅：横版，点按切换")
        compose.onNodeWithTag("test-preview-rotation").assertTextEquals("90")
        assertEquals(1, factories)
    }

    @Test fun zoomRailUsesActualRangeAndUpdatesThroughNativeSliderAndCommonStops() {
        var ratio by mutableFloatStateOf(1f)
        var enabled by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraZoomRail(ratio, 1f, 4f, enabled, { ratio = it }, false, Modifier.size(340.dp, 52.dp))
        } }
        compose.onNodeWithTag("camera-zoom-stop/2").assertIsEnabled().performClick()
        assertEquals(2f, ratio, .001f)
        compose.onNodeWithTag("camera-zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        assertEquals(4f, ratio, .001f)
        compose.onNodeWithTag("camera-zoom-value").assertTextEquals("4.0×")
        compose.onNodeWithTag("camera-zoom-stop/1").performClick()
        assertEquals(1f, ratio, .001f)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("camera-zoom-slider").assertIsNotEnabled()
        compose.onNodeWithTag("camera-zoom-stop/2").assertIsNotEnabled()
        compose.onNodeWithTag("camera-zoom-stop/0").assertDoesNotExist()
    }

    @Test fun vendorProcessingSettingsOnlyOfferModesTheBoundCameraSupports() {
        val prefs = compose.activity.getSharedPreferences("camera-interaction-test", 0).apply { edit().clear().commit() }
        val options = CameraOptions(prefs).apply { showSettings() }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraSettingsPanel(options, true, availableProcessing = setOf(CameraProcessing.STANDARD, CameraProcessing.HDR))
        } }
        compose.onNodeWithContentDescription("照片处理：HDR").performScrollTo().assertIsEnabled().performClick()
        assertEquals(CameraProcessing.HDR, options.processing)
        compose.onNodeWithContentDescription("照片处理：夜景").assertDoesNotExist()
        compose.onNodeWithContentDescription("照片处理：自动优化").assertDoesNotExist()
    }

    @Test fun nativeFilterStrengthChangesTheEffectiveMatrixAndRemainsSelectedAcrossOriginalMode() {
        var filter by mutableStateOf(PhotoFilter.WARM); var strength by mutableFloatStateOf(1f)
        var enabled by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraFilters(filter, enabled, { filter = it }, strength = strength, changeStrength = { strength = it })
        } }
        compose.onNodeWithTag("camera-filter-strength").performSemanticsAction(SemanticsActions.SetProgress) { it(.25f) }
        assertEquals(.25f, strength, .001f)
        assertFalse(PhotoFilters.coefficients(filter, strength).contentEquals(PhotoFilters.coefficients(filter)))
        compose.onNodeWithTag("camera-filter/ORIGINAL").performClick()
        compose.onNodeWithTag("camera-filter-strength").assertDoesNotExist()
        compose.onNodeWithTag("camera-filter/BW").performClick()
        assertEquals(.25f, strength, .001f)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("camera-filter-strength").assertIsNotEnabled()
    }
}
