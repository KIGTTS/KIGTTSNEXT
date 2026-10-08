package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSettingsTest {
    @Test fun appearanceRoundTripsIncludingWallpaperToggle() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val settings = ThemeSettings(seed = 0xFF6750A4, contrast = ContrastLevel.MEDIUM, deeperDark = true, cornerLevel = CornerLevel.MEDIUM, wallpaperEnabled = true, mode = ThemeMode.LIGHT)
        ThemeSettingsStore(app).save(settings)
        assertEquals(settings, ThemeSettingsStore(app).load())
    }
    @Test fun defaultThemeUsesRequestedSeedAndDarkModeUsesBlack() {
        assertEquals("#00696E", ThemeSettings().hex)
        assertNotEquals(themeColors(ThemeSettings()).background, themeColors(ThemeSettings(deeperDark = true)).background)
        assertEquals(androidx.compose.ui.graphics.Color.Black, themeColors(ThemeSettings(deeperDark = true)).background)
    }
    @Test fun oldRadiusPreferencesMigrateToThreeLevelsAndDefaultToLarge() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val preferences = app.getSharedPreferences("appearance", 0)
        preferences.edit().clear().commit()
        val store = ThemeSettingsStore(app)
        assertEquals(CornerLevel.LARGE, store.load().cornerLevel)
        listOf(4f to CornerLevel.SMALL, 12f to CornerLevel.MEDIUM, 18f to CornerLevel.MEDIUM,
            20f to CornerLevel.LARGE, 34f to CornerLevel.LARGE).forEach { (radius, expected) ->
            preferences.edit().putFloat("radius", radius).commit()
            val migrated = store.load()
            assertEquals(expected, migrated.cornerLevel)
            store.save(migrated)
            assertEquals(expected.radius, preferences.getFloat("radius", 0f))
            assertEquals(migrated, store.load())
        }
    }
    @Test fun contrastChangesForegroundAndKeepsPlatformColorsAtDefault() {
        val base = themeColors(ThemeSettings())
        assertEquals(base, themeColors(ThemeSettings(wallpaperEnabled = true), base))
        assertNotEquals(base.onSurface, themeColors(ThemeSettings(contrast = ContrastLevel.HIGH)).onSurface)
    }
    @Test fun oldContinuousContrastMigratesToNativeLevels() {
        assertEquals(ContrastLevel.DEFAULT, ContrastLevel.fromLegacy(-1f))
        assertEquals(ContrastLevel.MEDIUM, ContrastLevel.fromLegacy(.6f))
        assertEquals(ContrastLevel.HIGH, ContrastLevel.fromLegacy(.9f))
    }
    @Test fun lightUsesNativeRolesAndIgnoresDeeperDark() {
        val settings = ThemeSettings(mode = ThemeMode.LIGHT)
        val light = themeColors(settings)
        val dark = themeColors(settings.copy(mode = ThemeMode.DARK))
        assertTrue(light.background.red > .9f)
        assertTrue(light.onSurface.red < .3f)
        assertTrue(dark.background.red < .2f)
        assertEquals(light.background, themeColors(settings.copy(deeperDark = true)).background)
        assertEquals(light.surfaceContainer, themeColors(settings.copy(deeperDark = true)).surfaceContainer)
        assertEquals(light.primary, themeColors(settings.copy(wallpaperEnabled = true), light).primary)
        assertTrue(ThemeMode.SYSTEM.usesDark(true))
        assertFalse(ThemeMode.SYSTEM.usesDark(false))
        assertFalse(ThemeMode.LIGHT.usesDark(true))
        assertTrue(ThemeMode.DARK.usesDark(false))
    }
}
