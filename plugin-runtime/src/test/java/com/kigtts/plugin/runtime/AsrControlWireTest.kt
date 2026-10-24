package com.kigtts.plugin.runtime

import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AsrControlWireTest {
    @Test fun resourceControlHonorsGrantAndProviderRoleAndFormalSettingsAreExposed() = runBlocking {
        val received = mutableListOf<AsrControl>()
        fun plugin(id: String, role: AsrRole) = object : KigPlugin {
            override val descriptor = PluginDescriptor(id, id, "")
            override val asr = object : ControlledAsrEngine {
                override val info = AsrInfo(role = role)
                override suspend fun control(command: AsrControl): Boolean { received += command; return true }
                override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = RecognitionResult("")
                override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = emptyFlow<RecognitionEvent>()
            }
            override val contributions = PluginContributions(settingsEntries = listOf(SettingsEntry("cloud", "云端账户", "accounts")))
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.NoAction
        }
        val provider = plugin("test.provider", AsrRole.PROVIDER)
        val service = plugin("test.other-service", AsrRole.SERVICE)
        val paths = PluginPaths(ApplicationProvider.getApplicationContext())
        val registry = PluginRegistry(listOf(provider, service))
        val broker = HostSpeechBroker("test.owner", registry, paths, allowTts = false, allowAsr = true)
        val denied = HostSpeechBroker("test.owner", registry, paths, allowTts = true, allowAsr = false)
        try {
            val command = AsrControl(AsrControlOp.PREPARE)
            val wire = IHostSpeechBroker.Stub.asInterface(broker.asBinder())
            assertTrue(wire.asrControl(provider.descriptor.id, PluginJson.encodeToString(command)))
            assertEquals(listOf(command), received)
            assertEquals("accounts", PluginJson.decodeFromString<List<AsrProvider>>(wire.asrProviders()).single().settingsPages.single().id)
            assertTrue(runCatching { denied.asrControl(provider.descriptor.id, PluginJson.encodeToString(command)) }.isFailure)
            assertTrue(runCatching { broker.asrControl(service.descriptor.id, PluginJson.encodeToString(command)) }.isFailure)
            assertTrue(runCatching { broker.asrControl("test.owner", PluginJson.encodeToString(command)) }.isFailure)
            assertEquals(1, received.size)
        } finally { broker.close(); denied.close() }
    }
}
