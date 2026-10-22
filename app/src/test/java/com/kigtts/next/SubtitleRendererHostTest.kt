package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.SubtitleBody
import com.kigtts.next.ui.home.cornerActions
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleRendererHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Renderer(private val id: String = "test.renderer") : KigPlugin {
        var lastContext: PluginContext? = null
        var outcome: PluginOutcome = PluginOutcome.SetSubtitleRenderer(PluginResource("renderer"))
        override val descriptor = PluginDescriptor(id, "测试字幕", "测试用", apiVersion = 17)
        override val contributions = PluginContributions(actions = listOf(UiAction("toggle", "灯珠字幕", UiSlot.EDITOR_TOP_START,
            order = -100, busy = true, symbol = "lightbulb")))
        override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
            lastContext = context
            return outcome
        }
    }
    private fun show(): Pair<AppViewModel, Renderer> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val plugin = Renderer()
        val model = AppViewModel(app, listOf(plugin))
        model.pluginManager.paths.storage(plugin.descriptor.id).directory("renderer").resolve("index.html")
            .writeText("<!doctype html><html><body>LED</body></html>")
        compose.setContent {
            val state = model.state.collectAsState().value
            MaterialTheme { SubtitleBody(state, model, Modifier.fillMaxSize()) }
        }
        return model to plugin
    }
    private fun invoke(model: AppViewModel, plugin: Renderer) {
        compose.runOnIdle { model.invoke(plugin.descriptor.id, "toggle", mapOf("host.subtitleRenderer" to "forged.owner")) }
        compose.waitUntil(5000) { model.state.value.busyPlugins.isEmpty() }
        compose.waitForIdle()
    }
    @Test fun rendererPreservesTheDocumentAndNativeEditGesturesAndDoesNotCreateMedia() {
        val (model, plugin) = show()
        compose.runOnIdle { model.text("正在显示的字幕") }
        invoke(model, plugin)
        compose.onNodeWithTag("plugin-subtitle-renderer").assertExists()
        assertEquals("正在显示的字幕", model.state.value.editorText)
        assertNull(model.state.value.mediaId)
        assertEquals("", plugin.lastContext!!.inputs["host.subtitleRenderer"])
        assertEquals(plugin.descriptor.id, model.context().inputs["host.subtitleRenderer"])
        val action = cornerActions(UiSlot.EDITOR_TOP_START, model).single()
        assertTrue(action.iconOnly); assertTrue(action.busy)
        compose.onNodeWithTag("subtitle-renderer-input").performClick()
        compose.onNodeWithTag("speech-editor").performTextReplacement("编辑后的文字")
        assertEquals("编辑后的文字", model.state.value.editorText)
        assertNotNull(model.subtitleRenderer.selection.value)
        compose.runOnIdle { model.editing(false) }
        compose.onNodeWithTag("plugin-subtitle-renderer").assertExists()
        plugin.outcome = PluginOutcome.SetSubtitleRenderer()
        invoke(model, plugin)
        compose.onNodeWithTag("plugin-subtitle-renderer").assertDoesNotExist()
        compose.onNodeWithTag("speech-editor").assertTextEquals("编辑后的文字")
    }
    @Test fun invalidOrForeignResourceCannotReplaceExistingRendererAndOwnerCannotClearAnotherOne() {
        val (model, plugin) = show()
        invoke(model, plugin)
        val before = model.subtitleRenderer.selection.value
        plugin.outcome = PluginOutcome.SetSubtitleRenderer(PluginResource("renderer", "other.plugin"))
        invoke(model, plugin)
        assertSame(before, model.subtitleRenderer.selection.value)
        plugin.outcome = PluginOutcome.SetSubtitleRenderer(PluginResource("../outside"))
        invoke(model, plugin)
        assertSame(before, model.subtitleRenderer.selection.value)
        compose.runOnIdle { model.subtitleRenderer.clear("other.plugin") }
        assertSame(before, model.subtitleRenderer.selection.value)
        compose.runOnIdle {
            model.registry.replace(listOf(Renderer()))
            model.subtitleRenderer.reconcile(model.registry)
        }
        assertNull(model.subtitleRenderer.selection.value)
        assertEquals("", model.context().inputs["host.subtitleRenderer"])
    }
}
