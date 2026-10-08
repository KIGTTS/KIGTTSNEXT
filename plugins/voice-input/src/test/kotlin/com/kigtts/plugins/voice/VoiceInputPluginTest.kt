package com.kigtts.plugins.voice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class VoiceInputPluginTest {
    @Test fun disabledPluginRegistersNoGesture() {
        val plugin = VoiceInputPlugin()
        assertTrue(PluginRegistry(listOf(plugin)).gestureActions(PluginGesture.QUICK_TEXT_LONG_PRESS, PluginContext("", "")).isEmpty())
        assertNull(plugin.asr)
    }
    @Test fun enableDisablePersistsAndChangesGestureContribution() = runTest {
        var saved = false
        val plugin = VoiceInputPlugin { saved = it }
        val context = PluginContext("", "")
        assertEquals(PluginOutcome.RefreshUi, plugin.execute("toggle", context))
        assertTrue(saved)
        assertEquals("input", plugin.contributions.gestures.single().id)
        assertIs<PluginOutcome.Message>(plugin.execute("input", context))
        plugin.execute("toggle", context)
        assertFalse(saved)
        assertTrue(plugin.contributions.gestures.isEmpty())
    }
}
