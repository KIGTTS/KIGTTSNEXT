package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import org.junit.Assert.assertEquals
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
