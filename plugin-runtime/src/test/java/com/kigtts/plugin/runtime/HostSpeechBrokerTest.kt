package com.kigtts.plugin.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HostSpeechBrokerTest {
    private class Plugin(id: String, role: TtsRole = TtsRole.PROVIDER) : KigPlugin {
        override val descriptor = PluginDescriptor(id, id, "test")
        override val tts = object : TtsEngine {
            override val info = TtsInfo(role = role)
            override fun synthesize(request: TtsRequest) = kotlinx.coroutines.flow.emptyFlow<SynthesisEvent>()
        }
        var control: suspend (VoiceCommand) -> PluginOutcome = { PluginOutcome.RefreshUi }
        override val voiceManager = object : VoiceManager {
            override suspend fun command(command: VoiceCommand) = control(command)
        }
        override val contributions = PluginContributions()
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun catalogIncludesProvidersAndRejectsServiceCycles() {
        val provider = Plugin("test.provider"); val service = Plugin("test.service", TtsRole.SERVICE)
        val broker = HostSpeechBroker("test.owner", PluginRegistry(listOf(provider, service)), PluginPaths(ApplicationProvider.getApplicationContext<Context>()))
        try {
            val catalog = PluginJson.decodeFromString<List<TtsProvider>>(broker.providers())
            assertEquals(listOf("test.provider"), catalog.map { it.pluginId })
            val command = PluginJson.encodeToString(VoiceCommand(VoiceOperation.REFRESH))
            assertTrue(runCatching { broker.command("test.service", command) }.isFailure)
            assertTrue(runCatching { broker.command("test.owner", command) }.isFailure)
        } finally { broker.close() }
    }
    @Test fun importIsCopiedIntoProviderScopeAndAlwaysClearsStaging() = runBlocking<Unit> {
        val paths = PluginPaths(ApplicationProvider.getApplicationContext<Context>())
        val owner = "test.speech-import-owner"; val provider = Plugin("test.speech-import-provider")
        val source = paths.storage(owner).file("resources/voice.zip").apply { parentFile!!.mkdirs(); writeText("voice") }
        val broker = HostSpeechBroker(owner, PluginRegistry(listOf(provider)), paths)
        var staged: java.io.File? = null
        try {
            for (failure in listOf(false, true)) {
                provider.control = { command ->
                    staged = paths.storage(provider.descriptor.id).file(requireNotNull(command.resourcePath))
                    assertEquals("voice", staged!!.readText()); assertNotEquals(source, staged)
                    if (failure) error("invalid voice") else PluginOutcome.RefreshUi
                }
                val result = runCatching { broker.command(provider.descriptor.id, PluginJson.encodeToString(VoiceCommand(VoiceOperation.IMPORT, resourcePath = "resources/voice.zip"))) }
                assertEquals(failure, result.isFailure)
                assertFalse(staged!!.exists()); assertTrue(source.isFile)
            }
        } finally { broker.close(); paths.storage(owner).root.deleteRecursively(); paths.storage(provider.descriptor.id).root.deleteRecursively() }
    }
    @Test fun sourcePathOutsideServiceDataIsRejectedBeforeProviderControl() {
        val provider = Plugin("test.safe-provider")
        provider.control = { error("must not be called") }
        val broker = HostSpeechBroker("test.safe-owner", PluginRegistry(listOf(provider)), PluginPaths(ApplicationProvider.getApplicationContext<Context>()))
        try {
            assertTrue(runCatching { broker.command(provider.descriptor.id,
                PluginJson.encodeToString(VoiceCommand(VoiceOperation.IMPORT, resourcePath = "../another/voice.zip"))) }.isFailure)
        } finally { broker.close() }
    }
    @Test fun perPackParameterCommandKeepsItsTargetAndDoesNotUseResourceStaging() {
        val provider = Plugin("test.audio-parameters")
        val paths = PluginPaths(ApplicationProvider.getApplicationContext<Context>())
        val broker = HostSpeechBroker("test.audio-service", PluginRegistry(listOf(provider)), paths)
        val command = VoiceCommand(VoiceOperation.UPDATE_PARAMETERS, packId = "voice-test", value = """{"volume":0.8}""")
        var received: VoiceCommand? = null
        provider.control = { received = it; PluginOutcome.RefreshUi }
        try {
            broker.command(provider.descriptor.id, PluginJson.encodeToString(command))
            assertEquals(command, received)
            assertNull(received!!.resourcePath)
            val forbidden = HostSpeechBroker("test.audio-service", PluginRegistry(listOf(provider)), paths, allowTts = false)
            try { assertTrue(runCatching { forbidden.command(provider.descriptor.id, PluginJson.encodeToString(command)) }.isFailure) }
            finally { forbidden.close() }
        } finally { broker.close() }
    }
}
