package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.KigttsApp
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
class SettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) } }
    }
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")

    @Test fun headerOpensSettingsDirectlyAndAllPluginEntrancesShareSettingsCards() {
        val model = show()
        val menu = compose.onNodeWithContentDescription("打开设置").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onNodeWithTag("app-drawer").assertDoesNotExist()
        compose.onNodeWithText("外观与交互").assertIsDisplayed()
        capture("settings-categories")
        compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination); assertEquals(listOf(Destination.Home), model.state.value.navigationStack) }
        back()
        assertEquals(menu, compose.onNodeWithContentDescription("打开设置").getUnclippedBoundsInRoot())
        // Side swipes cannot open a settings page.
        compose.onRoot().performTouchInput { swipeRight(startX = 1f, endX = width / 2f) }
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
    }

    @Test fun categoriesOpenThirdLevelAndPredictiveReturnCancelsAndCommitsToParent() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.navigate(Destination.Settings) }
        val documents = setOf(SettingsCategory.USER_AGREEMENT, SettingsCategory.PRIVACY_POLICY, SettingsCategory.OPEN_SOURCE_LICENSES)
        SettingsCategory.entries.filterNot { it in documents }.forEach { category ->
            compose.onNodeWithText(category.title).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Destination.SettingsDetail(category), model.state.value.destination)
                assertEquals(listOf(Destination.Home, Destination.Settings), model.state.value.navigationStack)
            }
            capture("settings-" + category.name.lowercase())
            val dispatcher = compose.activity.onBackPressedDispatcher
            compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
            compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .65f, BackEventCompat.EDGE_LEFT)) }
            capture("settings-" + category.name.lowercase() + "-return")
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            compose.runOnIdle { assertEquals(Destination.SettingsDetail(category), model.state.value.destination) }
            if (category == SettingsCategory.FEEDBACK) {
                compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(1100f, 100f, 0f, BackEventCompat.EDGE_RIGHT)) }
                compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(900f, 100f, .8f, BackEventCompat.EDGE_RIGHT)) }
                compose.runOnIdle { dispatcher.onBackPressed() }
            } else compose.onNodeWithContentDescription("返回上一页").performClick()
            compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination) }
        }
        compose.onNodeWithText("关于").performScrollTo().performClick()
        documents.forEach { category ->
            compose.onNode(hasText(category.title) and hasClickAction()).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Destination.SettingsDetail(category), model.state.value.destination)
                assertEquals(listOf(Destination.Home, Destination.Settings, Destination.SettingsDetail(SettingsCategory.ABOUT)), model.state.value.navigationStack)
            }
            compose.onNodeWithContentDescription("返回上一页").performClick()
            compose.runOnIdle { assertEquals(Destination.SettingsDetail(SettingsCategory.ABOUT), model.state.value.destination) }
        }
        back()
        compose.onNodeWithText("插件管理").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Destination.Plugins, model.state.value.destination) }
        compose.onNodeWithTag("managed-list/plugins").performScrollToNode(hasTestTag("managed-item/input.voice"))
        compose.onNodeWithTag("managed-item/input.voice").performClick()
        compose.onNodeWithContentDescription("开启语音输入").assertIsDisplayed()
        back()
        compose.runOnIdle { assertEquals(Destination.Plugins, model.state.value.destination) }
        back()
        back()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination); assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
    }

    @Test fun appearanceResetResetsAllVisualPreferencesAndKeepsFeedbackPreference() {
        val model = show()
        compose.runOnIdle {
            model.theme(ThemeSettings(seed = 0xFF984061, mode = ThemeMode.DARK, contrast = ContrastLevel.HIGH, deeperDark = true, cornerLevel = CornerLevel.SMALL))
            model.navigate(Destination.Settings)
        }
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("恢复默认外观").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(ThemeMode.SYSTEM, model.state.value.theme.mode)
            assertEquals(ThemeSettings(), model.state.value.theme)
        }
        back()
        compose.onNodeWithText(SettingsCategory.FEEDBACK.title).performScrollTo().performClick()
        compose.onNodeWithContentDescription("按键震动反馈").performClick().assertIsOff()
        compose.runOnIdle { assertFalse(testAppModel(ApplicationProvider.getApplicationContext()).state.value.hapticsEnabled) }
        back()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithContentDescription("圆角：小").performScrollTo().performClick()
        compose.onNodeWithText("恢复默认外观").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ThemeSettings(), model.state.value.theme); assertFalse(model.state.value.hapticsEnabled) }
    }
}
