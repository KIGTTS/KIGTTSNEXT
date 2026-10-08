package com.kigtts.plugins.listening

import com.kigtts.plugin.api.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ListeningPanelPluginTest {
    @Test fun dynamicStartLabelAndCompactSlotsAreIndependentOfEngine() {
        val plugin = ListeningPanelPlugin()
        assertNull(plugin.asr)
        assertNull(plugin.tts)
        val idle = plugin.contributions(PluginContext("", "", false)).actions
        assertEquals("开始聆听", idle.first().label)
        assertEquals(UiSlot.EDITOR_TOP_START, idle.first().slot)
        assertEquals(UiButtonStyle.ICON_TEXT, idle.first().buttonStyle)
        assertEquals("关闭聆听", plugin.contributions(PluginContext("", "", true)).actions.first().label)
        assertEquals("关闭聆听", plugin.contributions(PluginContext("", "", false, true)).actions.first().label)
        assertEquals(listOf(UiSlot.LISTENING_BOTTOM_START, UiSlot.LISTENING_BOTTOM_END), idle.drop(1).map { it.slot })
    }
    @Test fun controlsIssueTypedHostCommands() = runTest {
        val plugin = ListeningPanelPlugin(); val context = PluginContext("", "")
        mapOf("toggle" to HostCommand.TOGGLE_LISTENING, "close" to HostCommand.CLOSE_LISTENING_PANEL,
            "stream" to HostCommand.USE_STREAMING, "batch" to HostCommand.USE_BATCH).forEach { (id, command) ->
            assertEquals(PluginOutcome.HostAction(command), plugin.execute(id, context))
        }
        val menu = assertIs<PluginOutcome.Menu>(plugin.execute("mode", context))
        assertEquals(listOf("stream", "batch"), menu.items.map { it.id })
    }
}
