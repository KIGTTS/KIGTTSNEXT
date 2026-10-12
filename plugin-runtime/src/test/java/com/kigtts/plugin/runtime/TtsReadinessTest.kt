package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TtsReadinessTest {
    private class Plugin(val id: String, val supported: Boolean = true) : KigPlugin {
        val controls = mutableListOf<TtsControlOperation>()
        override val descriptor = PluginDescriptor(id, id, "test")
        override val tts = object : TtsEngine {
            override val info = TtsInfo(supportsControl = supported)
            override fun synthesize(request: TtsRequest) = emptyFlow<SynthesisEvent>()
            override suspend fun control(command: TtsControl) { controls += command.operation }
        }
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun automaticReadPreparesOnceAndSelectingAnotherPluginReleasesPrevious() = runTest {
        val first = Plugin("test.first"); val next = Plugin("test.next")
        val readiness = TtsReadiness(PluginRegistry(listOf(first, next)), backgroundScope)
        fun state(id: String, automatic: Boolean, text: String = "") = PluginContext(text, "", automaticRead = automatic, ttsPluginId = id)
        readiness.observe(state(first.id, false)); runCurrent(); assertTrue(first.controls.isEmpty())
        readiness.observe(state(first.id, true)); runCurrent()
        readiness.observe(state(first.id, true, "updated")); runCurrent()
        assertEquals(listOf(TtsControlOperation.PREPARE), first.controls)
        readiness.observe(state(first.id, false)); runCurrent(); assertEquals(1, first.controls.size)
        readiness.observe(state(next.id, true)); runCurrent()
        assertEquals(TtsControlOperation.RELEASE, first.controls.last())
        assertEquals(listOf(TtsControlOperation.PREPARE), next.controls)
    }
    @Test fun providerWithoutLifecycleSupportIsNeverSentControlCommands() = runTest {
        val plugin = Plugin("test.old", false)
        val readiness = TtsReadiness(PluginRegistry(listOf(plugin)), backgroundScope)
        readiness.observe(PluginContext("", "", automaticRead = true, ttsPluginId = plugin.id)); runCurrent()
        readiness.observe(PluginContext("", "")); runCurrent()
        assertTrue(plugin.controls.isEmpty())
    }
}
