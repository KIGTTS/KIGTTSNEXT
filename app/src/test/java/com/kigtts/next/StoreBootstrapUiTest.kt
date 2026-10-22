package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.plugins.control.*
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.plugins.StoreBootstrapPrompt
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
class StoreBootstrapUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun offersExplicitDownloadThenNativeInstallAndAllowsCancelDuringDownload() {
        val state = mutableStateOf<StoreBootstrapState?>(StoreBootstrapState(StoreBootstrapPhase.OFFER))
        var confirmations = 0; var dismissed = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { StoreBootstrapPrompt(state.value, { confirmations++ }, { dismissed++ }) } }
        compose.onNodeWithText("先安装插件商店").assertIsDisplayed()
        compose.onNodeWithText("下载插件商店").performClick()
        compose.runOnIdle { assertEquals(1, confirmations); state.value = StoreBootstrapState(StoreBootstrapPhase.DOWNLOADING) }
        compose.onNodeWithText("取消下载").assertIsEnabled()
        compose.onNodeWithText("重试").assertIsNotEnabled()
        compose.runOnIdle { state.value = StoreBootstrapState(StoreBootstrapPhase.REVIEW) }
        compose.onNodeWithText("安装并继续").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }
}
