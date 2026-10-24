package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.PluginStrokeGuide
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class StrokeGuideUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun tappingTheGuideOpensInstructionsWithoutTogglingOrChoosingText() {
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PluginStrokeGuide(PageBlock.StrokeGuide("M 形", "从左下开始，一笔画出 M 形。",
                listOf(StrokePoint(.1f, .9f), StrokePoint(.1f, .1f), StrokePoint(.5f, .7f), StrokePoint(.9f, .1f), StrokePoint(.9f, .9f)),
                toggle = ManagementToggle(false, "toggle"), actions = listOf(PageAction("choose", "选择快捷文本"))),
                true, { action, _ -> calls += action }, SettingPosition.SINGLE)
        } }
        compose.onNodeWithTag("stroke-toggle/M 形").performClick()
        assertEquals(listOf("toggle"), calls)
        compose.onNodeWithTag("stroke-guide/M 形").performClick()
        compose.onNodeWithText("从左下开始，一笔画出 M 形。").assertIsDisplayed()
        compose.onNodeWithTag("stroke-replay").assertIsDisplayed()
        assertEquals(listOf("toggle"), calls)
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithTag("stroke-replay").assertDoesNotExist()
    }
}
