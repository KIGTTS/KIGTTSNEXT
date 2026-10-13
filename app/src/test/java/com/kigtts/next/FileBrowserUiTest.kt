package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.files.*
import com.kigtts.next.ui.media.HtmlFilesScreen
import com.kigtts.next.ui.plugins.PluginFilesScreen
import com.kigtts.plugin.api.*
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
class FileBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun selectingAndCancellingNeverChangesFileRowHeightsOrTrailingCellSize() {
        var selected by mutableStateOf(emptyList<String>())
        val items = listOf(BrowserFile("a", "无副标题", false), BrowserFile("b", "页面", false, "主页面"))
        compose.setContent { KigttsTheme(ThemeSettings()) { FileBrowserRows(items, selected, true,
            { path -> selected = if (path in selected) selected - path else selected + path }, {}, { _, _ -> }, Modifier.fillMaxSize()) } }
        val original = items.map { compose.onNodeWithTag("file/${it.path}").getUnclippedBoundsInRoot() }
        assertEquals(72f, (original[0].bottom - original[0].top).value, .5f)
        assertEquals(original[0].bottom - original[0].top, original[1].bottom - original[1].top)
        compose.onNodeWithTag("file/a").performTouchInput { longClick() }
        assertEquals(listOf("a"), selected)
        assertEquals(original, items.map { compose.onNodeWithTag("file/${it.path}").getUnclippedBoundsInRoot() })
        compose.onNodeWithTag("file/a").performClick()
        assertTrue(selected.isEmpty())
        assertEquals(original, items.map { compose.onNodeWithTag("file/${it.path}").getUnclippedBoundsInRoot() })
    }
    @Test fun pluginDestinationPickerKeepsTheSourcePathsAcrossDirectoryUpdates() {
        var files by mutableStateOf(PageBlock.Files("文件", listOf(PluginFileItem("assets", "assets", true),
            PluginFileItem("photo.png", "photo.png", false, 20)), "", "open", "operation", parentAction = "parent"))
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        compose.setContent { KigttsTheme(ThemeSettings()) { PluginFilesScreen(files, true) { action, inputs ->
            calls += action to inputs
            if (action == "open" && inputs["path"] == "assets") files = files.copy(directory = "assets", items = emptyList())
            if (action == "parent") files = files.copy(directory = "", items = listOf(PluginFileItem("assets", "assets", true)))
        } } }
        compose.onNodeWithTag("plugin-file-menu/photo.png").performClick()
        compose.onNodeWithText("移动到…").performClick()
        compose.onNodeWithTag("destination-folder/assets").performClick()
        compose.waitUntil(5_000) { !compose.onNodeWithText("移动到这里").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithText("移动到这里").performClick()
        compose.waitUntil(5_000) { calls.any { it.first == "operation" } }
        val request = calls.single { it.first == "operation" }.second
        assertEquals("move", request["op"]); assertEquals("assets", request["destination"])
        assertEquals(listOf("photo.png"), PluginJson.decodeFromString<List<String>>(request.getValue("paths")))
        compose.onNodeWithText("移动文件").assertDoesNotExist()
    }
    @Test fun htmlMoveIsVisibleAndTracksTheSelectedMainPage() {
        val app = ApplicationProvider.getApplicationContext<Application>(); val model = testAppModel(app)
        val entry = model.mediaRepository.html("项目", "<p>主页面</p>"); val files = model.mediaRepository.htmlFiles
        files.create(entry.id, "", "assets", true)
        compose.setContent { KigttsTheme(ThemeSettings()) { HtmlFilesScreen(model, entry.id, "display") } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("html-file/index.html").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("html-file-menu/index.html").performClick()
        compose.onNodeWithText("移动到…").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("destination-folder/assets").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("destination-folder/assets").performClick()
        compose.waitUntil(5_000) { !compose.onNodeWithText("移动到这里").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithText("移动到这里").performClick()
        compose.waitUntil(5_000) { files.main(entry.id) == "assets/index.html" }
        assertEquals("<p>主页面</p>", files.read(entry.id, "assets/index.html"))
        compose.onNodeWithText("移动文件").assertDoesNotExist()
        model.mediaRepository.remove(entry.id)
    }
    @Test fun destinationDisappearingShowsAnErrorWithoutSilentlyLosingTheSource() {
        val app = ApplicationProvider.getApplicationContext<Application>(); val model = testAppModel(app)
        val entry = model.mediaRepository.html("项目", "正文"); val files = model.mediaRepository.htmlFiles
        files.create(entry.id, "", "target", true)
        compose.setContent { KigttsTheme(ThemeSettings()) { HtmlFilesScreen(model, entry.id, "display") } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("html-file/index.html").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("html-file-menu/index.html").performClick(); compose.onNodeWithText("移动到…").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("destination-folder/target").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("destination-folder/target").performClick(); compose.waitForIdle()
        assertTrue(files.file(entry.id, "target").delete())
        compose.onNodeWithText("移动到这里").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("目标文件夹已不存在").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("移动文件").assertIsDisplayed(); assertEquals("正文", files.read(entry.id, "index.html"))
        compose.onNodeWithText("取消").performClick(); model.mediaRepository.remove(entry.id)
    }
}
