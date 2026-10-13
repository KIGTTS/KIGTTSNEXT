package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.plugins.PluginSourceEditorScreen
import com.kigtts.plugin.api.PageBlock
import kotlinx.coroutines.runBlocking
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
class EditorReturnSaveUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedHtmlReturnSaveKeepsItsVisibleDialogAndDraftThenARealRetryWritesAndLeaves() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val media = model.mediaRepository.html("页面", "旧HTML")
        runBlocking { model.content.editorFile(media.id, "index.html", "display") }
        compose.setContent { KigttsApp(model) }
        compose.onNodeWithTag("content-editor-text").performTextReplacement("返回保存的新HTML")
        val token = model.content.editorDraft.value!!.token
        val file = model.mediaRepository.htmlFiles.file(media.id, "index.html")
        assertTrue(file.delete())
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("save-changes-save").fetchSemanticsNodes().singleOrNull()?.config
            ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false }
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed().assertIsEnabled()
        compose.onNode(isDialog()).assertIsDisplayed()
        assertEquals(token, model.content.editorDraft.value!!.token)
        assertEquals("返回保存的新HTML", model.content.editorDraft.value!!.value)
        assertTrue(model.content.discardRequested.value)
        file.writeText("旧HTML", Charsets.UTF_8)
        compose.onNodeWithTag("save-changes-save").performClick()
        compose.waitUntil(8_000) { model.content.editorDraft.value == null }
        assertEquals("返回保存的新HTML", model.mediaRepository.htmlFiles.read(media.id, "index.html"))
        assertFalse(model.content.discardRequested.value)
        assertEquals(Destination.Home, model.state.value.destination)
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { model.content.editor() }
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun pluginSourceFailureDoesNotCloseTheModalAndOnlyAnAcknowledgedRetryReturns() {
        var block by mutableStateOf(PageBlock.SourceEditor("source", "旧源码", "save"))
        var fail = true
        var backs = 0
        val requests = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PageScaffold("源码", { backs++ }, largeBar = false) {
                PluginSourceEditorScreen(block, true) { _, input ->
                    val value = input.getValue("value"); requests += value
                    if (fail) throw IllegalStateException("测试保存失败")
                    block = block.copy(text = value)
                }
            }
        } }
        compose.onNodeWithTag("content-editor-text").performTextReplacement("新的源码")
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").performClick()
        compose.onNode(hasText("测试保存失败") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed().assertIsEnabled()
        assertEquals(0, backs)
        assertEquals("旧源码", block.text)
        fail = false
        compose.onNodeWithTag("save-changes-save").performClick()
        compose.waitUntil(8_000) { backs == 1 }
        assertEquals(listOf("新的源码", "新的源码"), requests)
        assertEquals("新的源码", block.text)
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag("content-editor-text").assertTextEquals("新的源码")
    }
}
