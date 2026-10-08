package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.ColorUtils
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
class CompactControlPaletteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightDefaultControlsHaveClearerBoundariesAndLegibleLabelsAcrossSeeds() {
        listOf(0xFF00696E, 0xFF6750A4, 0xFF386A20, 0xFF984061, 0xFFFBE3E7).forEach { seed ->
            val settings = ThemeSettings(seed = seed, mode = ThemeMode.LIGHT)
            val base = themeColors(settings, isDark = false)
            val controls = compactControlPalette(settings, dark = false, base = base)
            val before = ColorUtils.calculateContrast(base.secondaryContainer.toArgb(), base.surfaceContainer.toArgb())
            val after = ColorUtils.calculateContrast(controls.container.toArgb(), base.surfaceContainer.toArgb())
            assertTrue("Seed $seed improves control boundary: $before -> $after", after > before * 1.5)
            assertTrue("Small labels remain legible", ColorUtils.calculateContrast(controls.content.toArgb(), controls.container.toArgb()) >= 4.5)
        }
    }
    @Test fun darkAndExplicitContrastKeepNativeRolesAndWallpaperSourceIsRespected() {
        listOf(false, true).forEach { dark -> ContrastLevel.entries.forEach { contrast ->
            val settings = ThemeSettings(contrast = contrast, mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            val base = themeColors(settings, isDark = dark)
            val controls = compactControlPalette(settings, dark = dark, base = base)
            if (dark || contrast != ContrastLevel.DEFAULT) {
                assertEquals(base.secondaryContainer, controls.container)
                assertEquals(base.onSecondaryContainer, controls.content)
            }
        } }
        val wallpaper = themeColors(ThemeSettings(seed = 0xFF6750A4, mode = ThemeMode.LIGHT), isDark = false)
        val settings = ThemeSettings(mode = ThemeMode.LIGHT, wallpaperEnabled = true)
        assertNotEquals(compactControlPalette(settings, dark = false), compactControlPalette(settings, wallpaper, dark = false))
    }
    @Test fun compactPaletteTransitionsWithoutChangingDefaultPageContrast() {
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.LIGHT))
        var controls: CompactControlPalette? = null
        var pageColor: androidx.compose.ui.graphics.Color? = null
        compose.setContent { KigttsTheme(settings) {
            val current = LocalCompactControlPalette.current
            val surface = MaterialTheme.colorScheme.surfaceContainer
            SideEffect { controls = current; pageColor = surface }
        } }
        val before = controls!!
        assertEquals(themeColors(settings, isDark = false).surfaceContainer, pageColor)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { settings = settings.copy(seed = 0xFF984061) }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(180); compose.waitForIdle()
        val target = compactControlPalette(settings, dark = false)
        assertNotEquals(before.container, controls!!.container)
        assertNotEquals(target.container, controls!!.container)
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        assertEquals(target, controls)
        assertEquals(ContrastLevel.DEFAULT, settings.contrast)
        compose.mainClock.autoAdvance = true
    }
    @Test fun smallHomeAndPluginActionsUseTheImprovedLightPalette() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = AppViewModel(app)
        model.theme(ThemeSettings(mode = ThemeMode.LIGHT))
        compose.setContent { KigttsApp(model) }
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.toggleListening(); model.invoke("debug.ui", "EDITOR_TOP_START/add") }
        compose.onNodeWithTag("debug.ui/EDITOR_TOP_START/0").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/light-home-compact-controls.png")
        compose.onNodeWithContentDescription("编辑快捷文本").performClick()
        compose.onRoot().captureRoboImage("build/previews/light-compact-editor.png")
        compose.runOnIdle { assertEquals(ContrastLevel.DEFAULT, model.state.value.theme.contrast) }
    }
}
