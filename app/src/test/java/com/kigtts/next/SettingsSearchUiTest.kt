package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsSearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun focusedSearchShowsTheFullPathAndTargetsTheActualOption() {
        var destination: Destination? = null; var visible by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            if (visible) SettingsSearchDialog(emptyList(), { visible = false }, { destination = it })
        } }
        compose.onNodeWithTag("settings-search-input").assertIsFocused().performTextInput("背景图片")
        compose.onNodeWithText("设置 / 外观 / 背景与字体 / 背景图片").assertIsDisplayed()
        compose.onNode(hasText("背景图片") and hasClickAction() and hasText("设置 / 外观 / 背景与字体 / 背景图片")).performClick()
        assertEquals(Destination.SettingsDetail(SettingsCategory.APPEARANCE), destination)
        assertEquals("背景图片", SettingFocus.target?.title)
        assertFalse(visible); SettingFocus.target = null
    }
}
