package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CameraSettingsGridUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun flashMotionAndFiltersHaveEntriesInTheCameraSettingsGrid() {
        val preferences = compose.activity.getSharedPreferences("camera-shooting-settings-test", 0)
        preferences.edit().clear().commit()
        val options = CameraOptions(preferences)
        val hasFlash = mutableStateOf(true)
        var filterRequests = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalUiLocale provides "en") {
                CameraSettingsGrid(options, flashAvailable = hasFlash.value, enabled = true,
                    availableProcessing = setOf(CameraProcessing.STANDARD), manual = null, exposureWarningAvailable = true,
                    openFilters = { filterRequests++ }, openWatermark = {})
            }
        } }
        compose.onNodeWithText("Flash").assertIsEnabled().performClick()
        compose.onNodeWithText("Motion photo").assertIsEnabled().performClick()
        compose.onNodeWithText("Filters").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(CameraFlash.AUTO, CameraOptions(preferences).flash)
            assertTrue(CameraOptions(preferences).motion)
            assertEquals(1, filterRequests)
            hasFlash.value = false
        }
        compose.onNodeWithText("Flash").assertIsNotEnabled()
    }

    @Test fun videoStabilizationDefaultsOnPersistsAndFollowsLensSupport() {
        val preferences = compose.activity.getSharedPreferences("camera-stabilization-test", 0)
        val options = CameraOptions(preferences)
        val supported = mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalUiLocale provides "en") {
                CameraSettingsGrid(options, flashAvailable = false, enabled = true,
                    availableProcessing = setOf(CameraProcessing.STANDARD), manual = null,
                    exposureWarningAvailable = true, videoStabilizationAvailable = supported.value, openWatermark = {})
            }
        } }
        val button = compose.onNodeWithText("Video stabilization")
        button.assertDoesNotExist()
        compose.runOnIdle { assertTrue(options.videoStabilization); options.chooseMode(CameraMode.VIDEO) }
        button.assertIsEnabled().performClick()
        compose.runOnIdle {
            assertFalse(CameraOptions(preferences).videoStabilization)
            options.chooseMode(CameraMode.TIME_LAPSE)
            supported.value = false
        }
        button.assertIsNotEnabled()
        compose.onNodeWithText("Unavailable on this lens", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun narrowGridCentersMultilineEnglishCopyAndUpdatesExistingTilesWhenLanguageChanges() {
        val locale = mutableStateOf("en")
        val options = CameraOptions(compose.activity.getSharedPreferences("camera-settings-copy-test", 0)).apply {
            chooseMode(CameraMode.PRO)
        }
        compose.setContent {
            KigttsTheme(ThemeSettings()) {
                CompositionLocalProvider(LocalUiLocale provides locale.value) {
                    Box(Modifier.width(270.dp)) {
                        CameraSettingsGrid(options, flashAvailable = true, enabled = true,
                            availableProcessing = setOf(CameraProcessing.STANDARD), manual = null,
                            exposureWarningAvailable = false, openWatermark = {})
                    }
                }
            }
        }
        for (label in listOf("Exposure warning", "Unavailable on this lens")) {
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertTrue(layout.single().lineCount > 1)
            assertEquals(TextAlign.Center, layout.single().layoutInput.style.textAlign)
        }
        compose.runOnIdle { locale.value = "ko" }
        compose.onNodeWithText("삼분할", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("노출 경고", useUnmergedTree = true).assertIsDisplayed()
    }
}
