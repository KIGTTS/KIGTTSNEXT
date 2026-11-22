package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.navigation.*
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

    @Test fun activityThemeOnlyPublishesTheNewPaletteToCompositionWhileDrawingInterpolates() {
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.DARK))
        val target = settings.copy(seed = 0xff89613f, contrast = ContrastLevel.HIGH)
        val first = themeColors(settings, isDark = true).primary
        val last = ThemePalettes.resolve(ThemePaletteKey(target.effectiveSeed, target.contrast, false, true, null)).colors.primary
        val composed = linkedSetOf<Color>()
        val painted = linkedSetOf<Color>()
        compose.setContent {
            KigttsTheme(settings, publishWebTheme = true) {
                val color = MaterialTheme.colorScheme.primary
                val paint = requireNotNull(LocalThemePaint.current)
                SideEffect { composed += color }
                Box(Modifier.fillMaxSize().drawBehind {
                    val current = paint.palette.colors.primary
                    painted += current
                    drawRect(current)
                })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { settings = target }
        repeat(7) { compose.mainClock.advanceTimeBy(80); capture("activity-palette-draw") }
        compose.runOnIdle {
            assertEquals(setOf(first, last), composed)
            assertTrue("The live layer drew intermediate colors: $painted", painted.any { it != first && it != last })
            assertEquals(last, painted.last())
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun foregroundTextAndIconBothPaintIntermediateThemeColors() {
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.DARK))
        val updated = settings.copy(seed = 0xffff6b6b)
        ThemePalettes.resolve(ThemePaletteKey(updated.effectiveSeed, updated.contrast, false, true, null))
        compose.setContent {
            KigttsTheme(settings, publishWebTheme = true) {
                val primary = MaterialTheme.colorScheme.primary
                Column(Modifier.background(Color.Black)) {
                    ThemeText("MMMM", Modifier.testTag("animated-text"), color = primary,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp))
                    ThemeIcon(Icons.Default.Favorite, "Favorite", Modifier.size(48.dp).testTag("animated-icon"), tint = primary)
                }
            }
        }
        fun ink(tag: String): Int {
            val path = "build/previews/$tag.png"
            compose.onNodeWithTag(tag).captureRoboImage(path)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(path))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            return pixels.filter { it != Color.Black.toArgb() }.groupingBy { it }.eachCount().maxBy { it.value }.key
        }
        fun captureTints() = listOf(ink("animated-text"), ink("animated-icon"))
        val first = captureTints()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { settings = updated }
        val samples = List(4) { compose.mainClock.advanceTimeBy(84); captureTints() }
        compose.mainClock.advanceTimeBy(500)
        val last = captureTints()
        first.indices.forEach { index ->
            assertNotEquals(first[index], last[index])
            assertTrue(samples.any { it[index] != first[index] && it[index] != last[index] })
        }
        compose.mainClock.autoAdvance = true
    }

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

    @Test fun predictiveProgressMovesTheLayerWithoutRecomposingTheHost() {
        var hostCompositions = 0
        val entry = PageEntry(PageScene.Screen(Destination.Settings), 1)
        compose.setContent {
            val back = rememberPredictiveReturn(enabled = true, identity = entry.identity) {}
            SideEffect { hostCompositions++ }
            KigttsTheme(ThemeSettings()) {
                PageMotion(entry, PageRole.FOREGROUND, true, { back.value }, { 0f }, {}) {
                    Box(Modifier.fillMaxSize().testTag("gesture-layer-content"))
                }
            }
        }
        val content = compose.onNodeWithTag("gesture-layer-content")
        val initial = content.getUnclippedBoundsInRoot().left
        val composed = compose.runOnIdle { hostCompositions }
        predict(.25f)
        val first = content.getUnclippedBoundsInRoot().left
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .75f, BackEventCompat.EDGE_LEFT)) }
        assertTrue(first > initial)
        assertTrue(content.getUnclippedBoundsInRoot().left > first)
        compose.runOnIdle { assertEquals(composed, hostCompositions) }
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        assertEquals(initial, content.getUnclippedBoundsInRoot().left)
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
