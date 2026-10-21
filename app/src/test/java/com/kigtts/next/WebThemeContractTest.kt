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
