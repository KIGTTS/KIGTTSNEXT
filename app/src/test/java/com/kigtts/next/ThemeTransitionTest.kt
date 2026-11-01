package com.kigtts.next

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import com.kigtts.next.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeTransitionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun modeSourceAndContrastChangesAlwaysReachTheLatestRequestedPalette() {
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.LIGHT))
        var observed: ColorScheme? = null
        compose.setContent { KigttsTheme(settings) {
            val colors = MaterialTheme.colorScheme
            SideEffect { observed = colors }
        } }
        val requests = listOf(settings.copy(mode = ThemeMode.DARK),
            settings.copy(background = "background", backgroundSeed = 0xffa14819,
                backgroundColorEnabled = true, contrast = ContrastLevel.HIGH, mode = ThemeMode.LIGHT),
            settings.copy(seed = 0xff24488a, contrast = ContrastLevel.MEDIUM, mode = ThemeMode.DARK))
        requests.forEach { value ->
            compose.runOnIdle { settings = value }
            compose.waitForIdle()
        }
        val target = themeColors(requests.last(), isDark = true)
        compose.waitUntil(5000) { observed?.primary == target.primary && observed?.surface == target.surface }
        assertEquals(target.onSurface, observed!!.onSurface)
        assertEquals(target.primaryContainer, observed!!.primaryContainer)
    }
    @Test fun systemModeTracksConfigurationAndExplicitModesOverrideIt() {
        var configuration by mutableStateOf(Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO })
        var settings by mutableStateOf(ThemeSettings())
        var colors: ColorScheme? = null
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                KigttsTheme(settings) { val current = MaterialTheme.colorScheme; SideEffect { colors = current } }
            }
        }
        assertEquals(themeColors(settings, isDark = false).background, colors!!.background)
        compose.runOnIdle { configuration = Configuration(configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_YES } }
        compose.waitForIdle()
        compose.waitUntil(5000) { colors?.background == themeColors(settings, isDark = true).background }
        assertEquals(themeColors(settings, isDark = true).background, colors!!.background)
        compose.runOnIdle { settings = settings.copy(mode = ThemeMode.LIGHT, deeperDark = true) }
        compose.waitForIdle()
        compose.waitUntil(5000) { colors?.background == themeColors(settings, isDark = false).background }
        assertEquals(themeColors(settings, isDark = false).background, colors!!.background)
        compose.runOnIdle { configuration = Configuration(configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_NO }; settings = settings.copy(mode = ThemeMode.DARK) }
        compose.waitForIdle()
        compose.waitUntil(5000) { colors?.background == themeColors(settings, isDark = true).background }
        assertEquals(themeColors(settings, isDark = true).background, colors!!.background)
    }
    @Test fun allThemeRolesTransitionThroughIntermediateColorsAndReachTarget() {
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.DARK))
        var observed: ColorScheme? = null
        compose.setContent { KigttsTheme(settings) {
            val colors = MaterialTheme.colorScheme
            SideEffect { observed = colors }
        } }
        val before = observed!!
        val targetSettings = ThemeSettings(seed = 0xFF6750A4, contrast = ContrastLevel.MEDIUM, mode = ThemeMode.DARK)
        val target = themeColors(targetSettings)
        // This case checks the animation clock; the system-mode case covers asynchronous generation.
        ThemePalettes.resolve(ThemePaletteKey(targetSettings.effectiveSeed, targetSettings.contrast,
            targetSettings.deeperDark, true, null))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { settings = targetSettings }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        val middle = observed!!
        assertNotEquals(before.primary, middle.primary)
        assertNotEquals(target.primary, middle.primary)
        assertNotEquals(before.surfaceContainer, middle.surfaceContainer)
        assertNotEquals(target.surfaceContainer, middle.surfaceContainer)
        assertNotEquals(before.onSurface, middle.onSurface)
        assertNotEquals(target.onSurface, middle.onSurface)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(target.primary, observed!!.primary)
        assertEquals(target.onSurface, observed!!.onSurface)
        assertEquals(target.surfaceContainer, observed!!.surfaceContainer)
        assertEquals(target.secondary, observed!!.secondary)
        assertEquals(target.tertiaryFixed, observed!!.tertiaryFixed)
        compose.mainClock.autoAdvance = true
    }
}
