package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.LandscapeAppRail
import com.kigtts.next.ui.navigation.AppHeader
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
class AgentHomeTaskUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun taskIndicatorIsBesideSettingsAndRemainsClickableWhenItBecomesPinned() = check(false)
    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun landscapeTaskIndicatorFollowsTheSettingsEndOfTheRail() = check(true)
    private fun check(landscape: Boolean) {
        var visible by mutableStateOf(false); var running by mutableStateOf(false); var revision by mutableIntStateOf(0)
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.agent", "助手", "test")
            override val contributions get() = PluginContributions(actions = if (visible)
                listOf(UiAction("open", "AI 助手", UiSlot.HEADER_END, PluginIcon.ROBOT2, busy = running)) else emptyList())
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.OpenPage("chat")
            override fun page(pageId: String, context: PluginContext) = PluginPage(pageId, "助手", emptyList())
        }
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        compose.setContent { MaterialTheme {
            val state = AppState(pluginRevision = revision)
            if (landscape) LandscapeAppRail(state, model, {}) else AppHeader(state, model, {})
        } }
        compose.onNodeWithTag("test.agent/open").assertDoesNotExist()
        compose.runOnIdle { running = true; visible = true; revision++ }
        compose.onNodeWithTag("test.agent/open").assertIsDisplayed().assertIsEnabled()
        assertEquals("正在执行", compose.onNodeWithTag("test.agent/open").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        val button = compose.onNodeWithTag("test.agent/open").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("open-settings").fetchSemanticsNode().boundsInRoot
        if (landscape) assertTrue(button.top >= settings.bottom) else assertTrue(button.right <= settings.left)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { running = false; revision++ }
        repeat(10) { compose.mainClock.advanceTimeByFrame(); compose.onNodeWithTag("test.agent/open").assertIsEnabled() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("test.agent/open").performClick()
        compose.waitUntil { model.state.value.destination == Destination.PluginPage("test.agent", "chat") }
    }
}
