package com.kigtts.plugin.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HostRecognitionBrokerTest {
    private fun plugin(id: String, role: AsrRole) = object : KigPlugin {
        override val descriptor = PluginDescriptor(id, id, "test")
        override val asr = object : AsrEngine {
            override val info = AsrInfo(role = role)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = RecognitionResult("provider")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = emptyFlow<RecognitionEvent>()
        }
        override val contributions = PluginContributions(drawerEntries = listOf(DrawerEntry("settings", "模型管理", "settings", PluginIcon.MIC)))
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun directoryContainsOnlyIndependentProvidersAndRejectsServiceCycles() {
        val provider = plugin("test.asr-provider", AsrRole.PROVIDER); val service = plugin("test.asr-service", AsrRole.SERVICE)
        val paths = PluginPaths(ApplicationProvider.getApplicationContext<Context>())
        val broker = HostRecognitionBroker("test.owner", PluginRegistry(listOf(provider, service)), paths)
        try {
            val catalog = broker.providers().filter { it.pluginId.startsWith("test.asr-") }
            assertEquals(listOf("test.asr-provider"), catalog.map { it.pluginId }); assertEquals("settings", catalog.single().settingsPages.single().id)
            assertSame(provider, broker.provider("test.asr-provider"))
            assertThrows(IllegalArgumentException::class.java) { broker.provider("test.asr-service") }
            assertThrows(IllegalArgumentException::class.java) { broker.provider("test.owner") }
            assertThrows(IllegalArgumentException::class.java) { broker.provider("test.missing") }
        } finally { broker.close() }
    }
    @Test fun synthesisAndRecognitionGrantsStaySeparate() {
        val paths = PluginPaths(ApplicationProvider.getApplicationContext<Context>())
        val recognition = HostSpeechBroker("test.owner", PluginRegistry(emptyList()), paths, allowTts = false, allowAsr = true)
        val synthesis = HostSpeechBroker("test.owner", PluginRegistry(emptyList()), paths)
        try {
            assertThrows(IllegalStateException::class.java) { recognition.providers() }
            assertThrows(IllegalStateException::class.java) { synthesis.asrProviders() }
            assertTrue(PluginJson.decodeFromString<List<AsrProvider>>(recognition.asrProviders()).none { it.info.role == AsrRole.SERVICE })
        } finally { recognition.close(); synthesis.close() }
    }

    @Test fun builtInServiceCanDeriveItsAvailabilityFromTheProviderDirectory() {
        val owner = "service.test-recognition"
        lateinit var directory: RecognitionBroker
        var readingServiceInfo = false
        val service = object : KigPlugin by plugin(owner, AsrRole.SERVICE) {
            override val asr = object : AsrEngine by requireNotNull(plugin(owner, AsrRole.SERVICE).asr) {
                override val info: AsrInfo
                    get() {
                        check(!readingServiceInfo) { "Recognition directory queried the calling service's info" }
                        readingServiceInfo = true
                        return try {
                            AsrInfo(role = AsrRole.SERVICE, availability = if (directory.providers().isEmpty())
                                EngineAvailability.NEEDS_RESOURCES else EngineAvailability.READY)
                        } finally { readingServiceInfo = false }
                    }
            }
        }
        val provider = plugin("test.asr-provider", AsrRole.PROVIDER)
        val registry = PluginRegistry(listOf(service, provider))
        val broker = HostSpeechBroker(owner, registry, PluginPaths(ApplicationProvider.getApplicationContext<Context>()))
        try {
            directory = broker.builtinRecognition()
            assertEquals(EngineAvailability.READY, service.asr.info.availability)
            assertEquals(listOf(provider.descriptor.id), directory.providers().map { it.pluginId })
            registry.replace(listOf(service))
            assertEquals(EngineAvailability.NEEDS_RESOURCES, service.asr.info.availability)
        } finally { broker.close() }
    }
}
