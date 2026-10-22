package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.ListeningPanel
import com.kigtts.next.ui.plugins.pluginBrowserDocument
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ListeningPluginPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Panel(val owner: String) : KigPlugin {
        override val descriptor = PluginDescriptor(owner, "副面板", "测试")
        override val contributions = PluginContributions(actions = listOf(UiAction("custom-button", "自定义按钮",
            UiSlot.LISTENING_BOTTOM_START, PluginIcon.STAR)))
        var lastContext: PluginContext? = null
        var result: PluginOutcome = PluginOutcome.SetListeningPanel("panel")
        override fun page(pageId: String, context: PluginContext) = if (pageId == "panel")
            PluginPage("panel", "副面板", listOf(PageBlock.Text("自定义副面板"), PageBlock.Text(context.transcript))) else null
        override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
            lastContext = context; return result
        }
    }
    private fun invoke(model: AppViewModel, plugin: Panel) {
        compose.runOnIdle { model.invoke(plugin.owner, "open", mapOf("host.listeningPanel" to "forged")) }
        compose.waitUntil(5000) { plugin.owner !in model.state.value.busyPlugins }; compose.waitForIdle()
    }
    @Test fun replacementOwnsOnlyItsControlsAndOverlayAndDisableRestoreNativeContent() {
        val plugin = Panel("test.listening")
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        compose.setContent { val state by model.state.collectAsState()
            MaterialTheme { Box(Modifier.fillMaxSize()) { ListeningPanel(state, model, 500f, extent = 420f) } } }
        compose.onNodeWithContentDescription("调整聆听字号").assertExists()
        invoke(model, plugin)
        compose.onNodeWithTag("plugin-listening-panel").assertExists()
        compose.onNodeWithText("自定义副面板").assertExists()
        compose.onNodeWithContentDescription("自定义按钮").assertExists()
        compose.onNodeWithContentDescription("调整聆听字号").assertDoesNotExist()
        compose.onNodeWithTag("listening-content").assertDoesNotExist()
        assertEquals("", plugin.lastContext!!.inputs["host.listeningPanel"])
        plugin.result = PluginOutcome.SetListeningPanel("panel", false, false)
        invoke(model, plugin)
        compose.onNodeWithTag("listening-content").assertExists()
        compose.onNodeWithContentDescription("调整聆听字号").assertExists()
        compose.onNodeWithTag("top-panel-resize").assertExists()
        plugin.result = PluginOutcome.HostAction(HostCommand.OPEN_LISTENING_PANEL)
        invoke(model, plugin)
        assertTrue(model.state.value.captionsVisible); assertFalse(model.state.value.listening)
        compose.runOnIdle { model.listeningPanel.reconcile(PluginRegistry(emptyList())) }
        compose.onNodeWithTag("plugin-listening-panel").assertDoesNotExist()
        compose.onNodeWithTag("listening-content").assertExists()
    }
    @Test fun nonexistentPagesAndForeignClearsCannotTakeOverAnExistingPanel() {
        val first = Panel("test.owner")
        val other = Panel("test.other")
        val state = ListeningPanelState()
        state.select(first, PluginOutcome.SetListeningPanel("panel"), PluginContext("", ""))
        state.select(other, PluginOutcome.SetListeningPanel(), PluginContext("", ""))
        assertEquals(first.owner, state.selection.value!!.owner)
        assertThrows(IllegalArgumentException::class.java) {
            state.select(other, PluginOutcome.SetListeningPanel("missing"), PluginContext("", ""))
        }
        state.reconcile(PluginRegistry(listOf(Panel(first.owner))))
        assertNull(state.selection.value)
    }
    @Test fun embeddedBrowserUsesExistingScopedResourcesAndNetworkDeclarations() {
        val plugin = Panel("test.browser")
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        val root = model.pluginManager.paths.storage(plugin.owner).directory("panel")
        root.resolve("index.html").writeText("<html>panel</html>")
        val document = pluginBrowserDocument(model, plugin.owner, "listening/panel",
            PageBlock.Browser(PluginResource("panel"), allowedActions = listOf("refresh")))
        assertEquals(root.canonicalFile, document.root!!.canonicalFile)
        assertEquals(listOf("refresh"), document.actions); assertFalse(document.network)
        assertThrows(IllegalArgumentException::class.java) { pluginBrowserDocument(model, plugin.owner, "panel",
            PageBlock.Browser(PluginResource("panel", "test.foreign"))) }
        assertThrows(IllegalArgumentException::class.java) { pluginBrowserDocument(model, plugin.owner, "panel",
            PageBlock.Browser(url = "https://example.org")) }
    }
}
