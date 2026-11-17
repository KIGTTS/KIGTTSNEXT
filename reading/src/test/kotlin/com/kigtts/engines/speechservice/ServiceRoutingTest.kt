package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ServiceRoutingTest {
    @get:Rule val files = TemporaryFolder()
    private class Broker : SpeechBroker {
        val controls = mutableListOf<Pair<String, TtsControl>>()
        var generation = "1"
        override fun providers() = listOf("piper", "other").map { TtsProvider(it, it, TtsInfo(supportsControl = true), generation = generation) }
        override suspend fun command(pluginId: String, command: VoiceCommand) = PluginOutcome.RefreshUi
        override fun synthesize(pluginId: String, request: TtsRequest) = emptyFlow<SynthesisEvent>()
        override suspend fun control(pluginId: String, command: TtsControl) { controls += pluginId to command }
    }
    private class System : TtsEngine {
        val controls = mutableListOf<TtsControl>()
        override fun synthesize(request: TtsRequest) = emptyFlow<SynthesisEvent>()
        override suspend fun control(command: TtsControl) { controls += command }
    }
    @Test fun selectingAnotherEngineReleasesOldEvenWhenResidentIsEnabled() = runBlocking {
        val settings = ServiceSettings(PluginStorage(files.newFolder())).apply { provider = "piper"; voice = "v1"; resident = true }
        val broker = Broker(); val system = System(); val routing = ServiceRouting(settings, broker, system)
        routing.control(TtsControl(TtsControlOperation.PREPARE))
        assertEquals(TtsControlOperation.SET_RESIDENT, broker.controls[0].second.operation)
        assertTrue(broker.controls[0].second.resident)
        assertEquals("v1", broker.controls[1].second.voiceId)
        settings.provider = "other"; settings.voice = "v2"
        routing.use { }
        assertEquals(TtsControl(TtsControlOperation.SET_RESIDENT, resident = false), broker.controls[2].second)
        assertEquals("piper" to TtsControl(TtsControlOperation.RELEASE), broker.controls[3])
        assertEquals("other" to TtsControl(TtsControlOperation.SET_RESIDENT, resident = true), broker.controls[4])
        settings.provider = null
        routing.use { }
        assertEquals("other", broker.controls.last().first)
        assertEquals(TtsControlOperation.RELEASE, broker.controls.last().second.operation)
        assertTrue(system.controls.last().resident)
        routing.close()
    }
    @Test fun voiceSwitchPreparesNewPackAndSameRouteRefreshDoesNotRewarmOrResetIdle() = runBlocking {
        val settings = ServiceSettings(PluginStorage(files.newFolder())).apply { provider = "piper"; voice = "v1" }
        val broker = Broker(); val routing = ServiceRouting(settings, broker, System())
        routing.use { }
        settings.voice = "v2"
        routing.use { }
        assertEquals(listOf(TtsControlOperation.SET_RESIDENT, TtsControlOperation.SET_RESIDENT, TtsControlOperation.RELEASE,
            TtsControlOperation.SET_RESIDENT, TtsControlOperation.PREPARE), broker.controls.map { it.second.operation })
        assertEquals("v2", broker.controls.last().second.voiceId)
        val before = broker.controls.size
        repeat(5) { routing.use { } }
        assertEquals(before, broker.controls.size)
        settings.resident = true; routing.use { }
        assertEquals(before, broker.controls.size) // The model was already loaded; only the host timer changed.
        routing.close()
    }
    @Test fun residentPreferencePersists() {
        val storage = PluginStorage(files.newFolder())
        assertFalse(ServiceSettings(storage).resident)
        assertEquals(2, ServiceSettings(storage).idleMinutes)
        ServiceSettings(storage).apply { resident = true; idleMinutes = 15; save() }
        val reopened = ServiceSettings(storage)
        assertTrue(reopened.resident); assertEquals(15, reopened.idleMinutes)
    }
    @Test fun restartedProviderReceivesResidentPolicyAndPreparationAgain() = runBlocking {
        val settings = ServiceSettings(PluginStorage(files.newFolder())).apply { provider = "piper"; resident = true }
        val broker = Broker(); val routing = ServiceRouting(settings, broker, System())
        routing.control(TtsControl(TtsControlOperation.PREPARE)); broker.controls.clear()
        broker.generation = "2"; routing.use { }
        assertEquals(listOf(TtsControlOperation.SET_RESIDENT, TtsControlOperation.RELEASE, TtsControlOperation.SET_RESIDENT,
            TtsControlOperation.PREPARE), broker.controls.map { it.second.operation })
        assertTrue(broker.controls[2].second.resident)
        routing.close()
    }
    @Test fun automaticReadPreparesNewProviderOnRoutingChangeWithoutRewarmingEveryUpdate() = runBlocking {
        val settings = ServiceSettings(PluginStorage(files.newFolder())).apply { provider = "piper" }
        val broker = Broker(); val routing = ServiceRouting(settings, broker, System())
        routing.control(TtsControl(TtsControlOperation.PREPARE))
        routing.changed(true)
        settings.provider = "other"; routing.use { }
        assertEquals("other", broker.controls.last().first)
        assertEquals(TtsControlOperation.PREPARE, broker.controls.last().second.operation)
        val count = broker.controls.size
        repeat(3) { routing.use { } }
        assertEquals(count, broker.controls.size)
        routing.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun configuredIdleCountdownStartsAfterSynthesisAndPlaybackFinish() = runTest {
        val settings = ServiceSettings(PluginStorage(files.newFolder())).apply { provider = "piper" }
        val broker = Broker(); val routing = ServiceRouting(settings, broker, System(), backgroundScope)
        val finish = CompletableDeferred<Unit>()
        val generation = launch { routing.use { finish.await() } }
        fun released() = broker.controls.count { it.second.operation == TtsControlOperation.RELEASE }
        try {
            runCurrent()
            advanceTimeBy(181_000); assertEquals(0, released())
            routing.control(TtsControl(TtsControlOperation.ACTIVITY, active = true))
            finish.complete(Unit); generation.join()
            advanceTimeBy(181_000); assertEquals(0, released())
            routing.control(TtsControl(TtsControlOperation.ACTIVITY, active = false))
            advanceTimeBy(119_999); assertEquals(0, released())
            advanceTimeBy(1); runCurrent(); assertEquals(1, released())

            settings.idleMinutes = 1
            routing.use { }
            advanceTimeBy(59_999); assertEquals(1, released())
            advanceTimeBy(1); runCurrent(); assertEquals(2, released())

            settings.resident = true
            routing.use { }
            advanceTimeBy(901_000); assertEquals(2, released())
        } finally { routing.close() }
    }
}
