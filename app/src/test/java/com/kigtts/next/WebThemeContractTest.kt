package com.kigtts.next

import com.kigtts.next.media.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebThemeContractTest {
    @Test fun customPaletteIncludesAllSemanticRolesAndUpdatesEvenWithinTheSameMode() {
        val palette = androidx.compose.material3.lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xff038387),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xff123456),
            tertiaryFixed = androidx.compose.ui.graphics.Color(0xff789abc))
        val colors = webThemeColors(palette)
        assertEquals(48, colors.size)
        assertEquals("#038387", colors["primary"])
        assertEquals("#123456", colors["on-primary-container"])
        assertEquals("#789abc", colors["tertiary-fixed"])
        assertTrue(colors.containsKey("surface-container-lowest"))
        assertTrue(colors.containsKey("on-error-container"))
        WebThemeState.current = WebTheme(false, colors)
        val replacement = WebTheme(false, webThemeColors(palette.copy(primary = androidx.compose.ui.graphics.Color.Red)))
        WebThemeState.current = replacement
        assertEquals(replacement, WebThemeState.themes.value)
        assertTrue(replacement.css().contains("--md-sys-color-primary:#ff0000"))
        assertTrue(replacement.script().contains("kigtts-theme-change"))
    }
    @Test fun actualPaletteAndModeAreExposedWithoutSensitiveState() {
        val theme = WebTheme(true, mapOf("primary" to "#aaffdd", "surface" to "#111111"), 12)
        assertTrue(theme.css().contains("--md-sys-color-primary:#aaffdd")); assertTrue(theme.css().contains("color-scheme:dark")); assertTrue(theme.css().contains("--kigtts-corner:12px"))
        assertTrue(theme.script().contains("kigtts-theme-change")); assertTrue(theme.script().contains("Object.freeze")); assertFalse(theme.script().contains("apiKey"))
    }
    @Test fun bundledResourcesWorkOfflineAndUnknownPathsDoNotExposeFiles() {
        WebThemeState.current = WebTheme(true)
        val response = WebUiResources.response(WebContentPolicy.ORIGIN + "/kigtts/theme.css") { error("theme must not read assets") }!!
        assertEquals("text/css", response.mimeType); assertTrue(response.data.bufferedReader().readText().contains("color-scheme:dark"))
        val denied = WebUiResources.response(WebContentPolicy.ORIGIN + "/kigtts/../private.json") { error("must not open") }!!
        assertEquals(404, denied.statusCode)
        assertNull(WebUiResources.response("https://example.com/kigtts/theme.css") { error("must not open") })
    }
}
