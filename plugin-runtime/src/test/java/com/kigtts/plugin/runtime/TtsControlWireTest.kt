package com.kigtts.plugin.runtime

import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class TtsControlWireTest {
    @Test fun brokerControlUsesStandardJsonAndRejectsUnauthorizedOwnerAndServiceRecursion() = runBlocking {
        val received = mutableListOf<TtsControl>()
        val provider = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.provider", "provider", "test")
            override val tts = object : TtsEngine {
                override val info = TtsInfo(supportsControl = true)
                override fun synthesize(request: TtsRequest) = emptyFlow<SynthesisEvent>()
                override suspend fun control(command: TtsControl) { received += command }
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val paths = PluginPaths(ApplicationProvider.getApplicationContext())
        val registry = PluginRegistry(listOf(provider))
        val broker = HostSpeechBroker("test.service", registry, paths)
        val command = TtsControl(TtsControlOperation.SET_RESIDENT, voiceId = "voice", resident = true)
        IHostSpeechBroker.Stub.asInterface(broker.asBinder()).ttsControl(provider.descriptor.id, PluginJson.encodeToString(command))
        assertEquals(listOf(command), received)
        assertTrue(runCatching { HostSpeechBroker("test.service", registry, paths, allowTts = false)
            .ttsControl(provider.descriptor.id, PluginJson.encodeToString(command)) }.isFailure)
        assertTrue(runCatching { HostSpeechBroker(provider.descriptor.id, registry, paths)
            .ttsControl(provider.descriptor.id, PluginJson.encodeToString(command)) }.isFailure)
        broker.close()
    }
}
