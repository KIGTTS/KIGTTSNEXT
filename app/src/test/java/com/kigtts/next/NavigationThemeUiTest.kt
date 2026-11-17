package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
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
class NavigationThemeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) } }
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun webThemeUsesTheSameWallpaperAndContrastPaletteAsTheApp() {
        val model = show()
        val context = compose.activity
        val settings = ThemeSettings(wallpaperEnabled = true, contrast = ContrastLevel.HIGH, mode = ThemeMode.DARK)
        compose.runOnIdle { model.theme(settings) }
        val wallpaper = androidx.compose.material3.dynamicDarkColorScheme(context)
        val expected = com.kigtts.next.media.webThemeColors(themeColors(settings, wallpaper, isDark = true))
        compose.waitUntil(timeoutMillis = 8_000) {
            com.kigtts.next.media.WebThemeState.current.colors == expected
        }
        assertTrue(com.kigtts.next.media.WebThemeState.current.dark)
    }
    @Test fun appearanceUsesMeasuredHomeWireframeWithoutListeningOrLabels() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.toggleListening() }
        compose.waitForIdle()
        compose.runOnIdle { model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        compose.onNodeWithTag("appearance-home-wireframe").assertIsDisplayed()
        capture("appearance-measured-home-wireframe")
    }
    private fun predict(progress: Float) {
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, progress, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
    }

    @Test fun settingsBackRestoresHomePanelAndCancelledPredictionKeepsPageOpen() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onNodeWithText("外观与交互").assertIsDisplayed()
        compose.onNodeWithTag("app-drawer").assertDoesNotExist()
        predict(.6f)
        capture("settings-direct-predictive-return")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination) }
        back()
        compose.onNodeWithContentDescription("打开设置").assertIsDisplayed()
        compose.runOnIdle { assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
        compose.runOnIdle { model.navigate(Destination.Settings) }
        compose.onNodeWithContentDescription("打开设置").assertDoesNotExist()
        back()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
    }

    @Test fun secondaryPredictionRevealsParentAndReturnWalksStackBeforeHomePanels() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.onNodeWithTag("editor-font-size").performClick()
        compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.Plugins) }
        val page = compose.onNodeWithTag("navigation-secondary")
        val normal = page.getUnclippedBoundsInRoot()
        predict(.65f)
        val moved = page.getUnclippedBoundsInRoot()
        assertTrue(moved.left > normal.left)
        // Unclipped test bounds keep layout size; the graphics-layer transform moves the page.
        capture("secondary-predictive-return")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { assertEquals(Destination.Plugins, model.state.value.destination) }
        compose.runOnIdle { model.navigate(Destination.PluginPage("input.voice", "settings")) }
        back()
        compose.runOnIdle { assertEquals(Destination.Plugins, model.state.value.destination) }
        back()
        compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination) }
        back()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination); assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
        compose.onNodeWithContentDescription("字幕字号滑条").assertIsDisplayed()
        back()
        compose.onNodeWithTag("editor-flip").assertIsDisplayed()
        compose.runOnIdle { assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
    }

    @Test fun appearanceModesPersistAndAllPageSurfacesUseBothPalettes() {
        val model = show()
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
            compose.onNodeWithTag("appearance-settings-sheet").performTouchInput { swipeUp() }
            compose.onNodeWithContentDescription("主题模式：" + mode.label).performScrollTo().performClick()
            compose.waitForIdle()
            val prefix = if (mode == ThemeMode.LIGHT) "light" else "dark"
            capture(prefix + "-settings")
            compose.runOnIdle {
                assertEquals(mode, ThemeSettingsStore(ApplicationProvider.getApplicationContext()).load().mode)
                val bars = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                assertEquals(mode == ThemeMode.LIGHT, bars.isAppearanceLightStatusBars)
                assertEquals(mode == ThemeMode.LIGHT, bars.isAppearanceLightNavigationBars)
                model.navigate(Destination.Home); model.toggleListening(); model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f)
            }
            capture(prefix + "-home")
            compose.runOnIdle { model.openSidePage(SidePage.SKETCH) }
            capture(prefix + "-sketch")
            back()
            compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
            capture(prefix + "-cards")
            back()
            compose.runOnIdle { model.navigate(Destination.Plugins) }
            capture(prefix + "-plugins")
            compose.runOnIdle { model.navigate(Destination.PluginPage("input.voice", "settings")) }
            capture(prefix + "-voice-input")
            compose.runOnIdle { model.navigate(Destination.Home); model.closeListeningPanel() }
        }
        compose.runOnIdle { model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        compose.onNodeWithTag("appearance-settings-sheet").performTouchInput { swipeUp() }
        compose.onNodeWithContentDescription("主题模式：跟随系统").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ThemeMode.SYSTEM, ThemeSettingsStore(ApplicationProvider.getApplicationContext()).load().mode) }
    }
}
