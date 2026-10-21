package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.home.PhraseMainPagePicker
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class UiIconsMainPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun offlineSymbolsHaveActualOutlinesAndSvgCopiesStayLocalAndRejectActiveContent() {
        val app = ApplicationProvider.getApplicationContext<Application>(); MaterialSymbols.initialize(app)
        assertTrue(MaterialSymbols.search("calculate").contains("calculate")); assertNotNull(MaterialSymbols.vector("calculate")); assertNull(MaterialSymbols.vector("does_not_exist_xyz"))
        val repo = MediaRepository(app, PluginPaths(app))
        val text = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="#00ff00"/></svg>"""
        val source = java.io.File(app.cacheDir, "manual-icon.svg").apply { writeText(text, Charsets.UTF_8) }
        val id = repo.icons.import(source); source.delete()
        val bitmap = requireNotNull(repo.icons.bitmap(id)); assertEquals(android.graphics.Color.GREEN, bitmap.getPixel(128, 128))
        assertNull(repo.icons.bitmap("../../outside"))
        listOf("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>",
            "<!DOCTYPE svg [<!ENTITY secret SYSTEM 'file:///private'>]><svg xmlns='http://www.w3.org/2000/svg'/>",
            "<svg xmlns='http://www.w3.org/2000/svg'><image href='https://example.com/a.png'/></svg>").forEach {
            assertTrue(runCatching { UiIcons.renderSvg(it, 256) }.isFailure)
        }
    }
    @Test fun mainPagePickerListsOnlyTheCurrentEntryProjectAndSetsNestedMain() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fixture = object : KigPlugin { override val descriptor = PluginDescriptor("test.fixture", "测试", "测试"); override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi }
        val model = AppViewModel(app, listOf(fixture)); val repo = model.mediaRepository
        val current = repo.html("当前词条", "first"); val another = repo.html("其它词条", "unrelated")
        repo.htmlFiles.create(current.id, "", "pages", true); repo.htmlFiles.create(current.id, "pages", "current-home.html", false)
        repo.htmlFiles.create(another.id, "", "foreign-home.html", false)
        var closed = false
        compose.setContent { KigttsTheme(ThemeSettings()) { PhraseMainPagePicker(model, current.id) { closed = true } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("pages").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("选择主页面").assertIsDisplayed(); compose.onNodeWithText("foreign-home.html").assertDoesNotExist()
        compose.onNodeWithText("pages").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("current-home.html").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("current-home.html").performClick(); compose.waitUntil(5000) { closed }
        assertEquals("pages/current-home.html", repo.htmlFiles.main(current.id)); assertEquals("index.html", repo.htmlFiles.main(another.id))
    }
    @Test fun svgClipReferenceCyclesAreRejectedBeforeRenderingButOrdinaryClipsStillWork() {
        val cycle = """<svg xmlns="http://www.w3.org/2000/svg"><defs><clipPath id="a"><g clip-path="url(#b)"><rect width="100" height="100"/></g></clipPath><clipPath id="b" clip-path="url(#a)"><rect width="100" height="100"/></clipPath></defs><rect width="100" height="100" clip-path="url(#a)"/></svg>"""
        assertThrows(IllegalArgumentException::class.java) { UiIcons.renderSvg(cycle, 100) }
        val valid = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><clipPath id="clip"><circle cx="50" cy="50" r="20"/></clipPath></defs><rect width="100" height="100" fill="#00ff00" clip-path="url(#clip)"/></svg>"""
        val bitmap = UiIcons.renderSvg(valid, 100)
        try { assertEquals(android.graphics.Color.GREEN, bitmap.getPixel(50, 50)); assertEquals(0, bitmap.getPixel(0, 0)) }
        finally { bitmap.recycle() }
    }
}
