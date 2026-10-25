package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.AppearanceSettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AppearancePreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun previewAndNestedCardsRemainVisibleAsSheetExpands() {
        var fonts = false
        compose.setContent {
            var settings by remember { mutableStateOf(ThemeSettings()) }
            KigttsTheme(settings) { Surface(Modifier.fillMaxSize()) { AppearanceSettingsScreen(settings, { settings = it }, onFonts = { fonts = true }) } }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.onNodeWithText("背景与字体").assertIsDisplayed()
        compose.onNodeWithTag("appearance-home-wireframe").assertIsDisplayed()
        compose.onAllNodes(hasText("点击输入文字") and hasAnyAncestor(hasTestTag("appearance-preview"))).assertCountEquals(0)
        compose.onNodeWithText("字体").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(fonts) }
        compose.onRoot().captureRoboImage("build/previews/appearance-preview-sheet.png")
        compose.onNodeWithTag("appearance-settings-sheet").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("主题与配色").assertExists()
    }
}
