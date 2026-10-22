package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.audio.SpeechServiceStatus
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.SpeechServicePrompt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SpeechServicePromptUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun laterAndPermanentHideAreDistinctAndAvailableBesideInstall() {
        var later = 0; var hidden = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { SpeechServicePrompt(SpeechServiceStatus.MISSING, {}, { later++ }, { hidden++ }) } }
        compose.onNodeWithText("前往安装").assertIsDisplayed()
        compose.onNodeWithText("不安装并隐藏朗读按钮").assertIsDisplayed()
        compose.onNodeWithText("稍后再说").performClick(); compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, later); assertEquals(0, hidden) }
    }
}
