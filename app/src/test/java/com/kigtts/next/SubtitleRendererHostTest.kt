package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.SubtitleBody
import com.kigtts.next.ui.home.TextEditor
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
    @Test fun pluginAppearanceIsPageScopedAndSavingRequiresHostApproval() {
        val (model, plugin) = show()
        val base = model.state.value.theme
        val patch = AppearancePatch(mode = AppearanceMode.DARK, backgroundDim = 1f, pureBlackDark = true)
        plugin.outcome = PluginOutcome.SetTemporaryAppearance(patch)
        invoke(model, plugin)
        assertEquals(patch, model.pageAppearance.value.single().appearance.patch)
        assertEquals(base, model.state.value.theme)
        compose.runOnIdle { model.navigate(Destination.Settings) }
        assertTrue(model.pageAppearance.value.isEmpty())

        plugin.outcome = PluginOutcome.ChangeSavedAppearance(patch)
        invoke(model, plugin)
        assertEquals(base, model.state.value.theme)
        compose.runOnIdle { model.answerSavedAppearance(requireNotNull(model.savedAppearance.value), false) }
        assertEquals(base, model.state.value.theme)
        invoke(model, plugin)
        compose.runOnIdle { model.answerSavedAppearance(requireNotNull(model.savedAppearance.value), true) }
        val saved = com.kigtts.next.theme.ThemeSettingsStore(compose.activity).load()
        assertEquals(base.copy(mode = com.kigtts.next.theme.ThemeMode.DARK, backgroundDim = 1f, deeperDark = true), saved)
    }

    @Test fun nativeRendererShrinksTheDockOnlyWhenFirstSelected() {
        val (model, plugin) = show()
        compose.runOnIdle {
            model.text("LED caption")
            model.openPanel(HomePanel.QUICK_TEXT)
        }
        val style = NativeSubtitleRenderer.Led()
        plugin.outcome = PluginOutcome.SetNativeSubtitleRenderer(style, collapseQuickText = true)
        invoke(model, plugin)
        compose.onNodeWithTag("native-led-renderer", useUnmergedTree = true).assertExists()
        assertNull(model.state.value.panel)
        assertEquals("LED caption", model.state.value.editorText)
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        plugin.outcome = PluginOutcome.SetNativeSubtitleRenderer(style.copy(glow = .7f), collapseQuickText = true)
        invoke(model, plugin)
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
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

    @Test fun rendererFillsTheRealEditorViewportAndKeepsItsViewDuringTextUpdates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val plugin = Renderer()
        val model = AppViewModel(app, listOf(plugin))
        model.pluginManager.paths.storage(plugin.descriptor.id).directory("renderer").resolve("index.html")
            .writeText("<!doctype html><html><body>LED</body></html>")
        val viewport = mutableStateOf(DpSize(360.dp, 500.dp))
        compose.setContent {
            val state = model.state.collectAsState().value
            MaterialTheme { Box(Modifier.size(viewport.value.width, viewport.value.height)) { TextEditor(state, model, false, false, Modifier.fillMaxSize()) } }
        }
        invoke(model, plugin)
        fun web(group: ViewGroup): WebView? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is WebView) return child
                if (child is ViewGroup) web(child)?.let { return it }
            }
            return null
        }
        val view = compose.runOnIdle { web(compose.activity.window.decorView as ViewGroup)!! }
        val bounds = compose.onNodeWithTag("plugin-subtitle-renderer").getUnclippedBoundsInRoot()
        assertEquals(compose.onNodeWithTag("subtitle-card").getUnclippedBoundsInRoot(), bounds)
        for (size in listOf(DpSize(360.dp, 240.dp), DpSize(300.dp, 600.dp))) {
            compose.runOnIdle { viewport.value = size }
            assertEquals("The background must meet all four card edges after resize",
                compose.onNodeWithTag("subtitle-card").getUnclippedBoundsInRoot(),
                compose.onNodeWithTag("plugin-subtitle-renderer").getUnclippedBoundsInRoot())
            assertSame(view, compose.runOnIdle { web(compose.activity.window.decorView as ViewGroup) })
        }
        // Robolectric does not lay out the AndroidView interop child; verify both Compose's
        // measured viewport and the real WebView's explicit native sizing contract.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.height)
        compose.runOnIdle { model.text("新的快捷文本") }
        compose.onNodeWithTag("plugin-subtitle-renderer").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "新的快捷文本"))
        compose.runOnIdle { model.content.send(QuickPhrase("绑定的快捷文本", model.quickTexts.categories.value.first())) }
        compose.onNodeWithTag("plugin-subtitle-renderer").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "绑定的快捷文本"))
        assertSame(view, compose.runOnIdle { web(compose.activity.window.decorView as ViewGroup) })
        compose.runOnIdle { model.subtitleRenderer.select(plugin.descriptor.id, plugin, model.subtitleRenderer.selection.value!!.root) }
        assertSame(view, compose.runOnIdle { web(compose.activity.window.decorView as ViewGroup) })
    }
}
