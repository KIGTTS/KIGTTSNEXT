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
        assertEquals(themeColors(settings, isDark = true).background, colors!!.background)
        compose.runOnIdle { settings = settings.copy(mode = ThemeMode.LIGHT, deeperDark = true) }
        compose.waitForIdle()
        assertEquals(themeColors(settings, isDark = false).background, colors!!.background)
        compose.runOnIdle { configuration = Configuration(configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_NO }; settings = settings.copy(mode = ThemeMode.DARK) }
        compose.waitForIdle()
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
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { settings = targetSettings }
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
