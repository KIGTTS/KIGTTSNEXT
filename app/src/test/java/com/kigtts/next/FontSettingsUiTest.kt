package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.settings.FontSettingsScreen
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun onlyAnAvailableStoreGetsDownloadGuidance() {
        var available by mutableStateOf(false)
        var calls = 0
        compose.setContent { MaterialTheme {
            FontSettingsScreen(ThemeSettings(), {}, if (available) ({ calls++; Unit }) else null)
        } }
        compose.onNodeWithText("从商店获取").assertDoesNotExist()
        compose.onNodeWithText("导入字体").assertExists()
        compose.runOnIdle { available = true }
        compose.onNodeWithText("从商店获取").performClick()
        compose.runOnIdle { Assert.assertEquals(1, calls); available = false }
        compose.onNodeWithText("从商店获取").assertDoesNotExist()
    }
}
