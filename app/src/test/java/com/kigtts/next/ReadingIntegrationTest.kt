package com.kigtts.next

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.engines.speechservice.SpeechServicePlugin
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingIntegrationTest {
    @Test fun builtInReadingReusesStandaloneSelectionAndRoutesProviderAudioInSafeMode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var lastRequest: TtsRequest? = null
        val provider = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.reading-provider", "测试声音", "")
            override val contributions = PluginContributions()
            override val tts = object : TtsEngine {
                override val info = TtsInfo(voices = listOf(VoiceInfo("saved-voice", "声音", "zh-CN")))
                override fun synthesize(request: TtsRequest) = flowOf(SynthesisEvent.Started,
                    SynthesisEvent.Audio(PcmAudio(byteArrayOf(2, 0))), SynthesisEvent.Finished())
                    .also { lastRequest = request }
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.NoAction
        }
        val reading = SpeechServicePlugin()
        val manager = PluginManager(context, listOf(reading, provider)).apply { safeMode = { true } }
        val storage = manager.paths.storage(reading.descriptor.id)
        val settings = storage.file("config/service.json").apply { parentFile!!.mkdirs() }
        val library = storage.file("config/voice-library.json")
        val oldSettings = """{"provider":"test.reading-provider","voice":"saved-voice","rate":1.25,"resident":false}"""
        val oldLibrary = """{"filter":"all","order":["test.reading-provider/pack"],"pins":{"test.reading-provider/pack":true}}"""
        settings.writeText(oldSettings); library.writeText(oldLibrary)
        try {
            assertEquals(TtsRole.SERVICE, reading.tts.info.role)
            assertNotNull(reading.page("manager", PluginContext("", "")))
            manager.reload()
            assertEquals("朗读设置", reading.page("manager", PluginContext("", ""))!!.title)
            assertTrue(manager.status.none { it.installed.manifest.id == reading.descriptor.id })
            assertTrue(manager.workers().isEmpty())
            assertTrue(reading.descriptor.id in manager.builtinIds)
            val audio = withTimeout(10_000) { reading.tts.synthesize(TtsRequest("保留旧设置")).toList() }
                .filterIsInstance<SynthesisEvent.Audio>()
            assertArrayEquals(byteArrayOf(2, 0), audio.single().audio.bytes)
            assertEquals("saved-voice", lastRequest!!.voiceId)
            assertEquals(1.25f, lastRequest!!.rate)
            assertEquals(oldSettings, settings.readText())
            assertEquals(oldLibrary, library.readText())
            manager.reload()
            assertSame(reading, manager.registry.plugin(reading.descriptor.id))
        } finally {
            manager.close()
            storage.root.deleteRecursively()
            manager.paths.storage(provider.descriptor.id).root.deleteRecursively()
        }
    }
}
