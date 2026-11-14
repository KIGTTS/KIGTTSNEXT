package com.kigtts.next

import android.app.Application
import android.media.*
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class AudioDeviceRoutingTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(AudioManager::class.java)
    private val shadow get() = shadowOf(manager)
    private fun device(type: Int) = AudioDeviceInfoBuilder.newBuilder().setType(type).build().also {
        val port: Any = org.robolectric.util.ReflectionHelpers.getField(it, "mPort")
        org.robolectric.util.ReflectionHelpers.setField(port, "mName", "")
        // Robolectric's public builder only maps output device types.
        if (type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            org.robolectric.util.ReflectionHelpers.setField(port, "mType", 0x80000004.toInt())
        }
    }
    @Before fun reset() { app.getSharedPreferences("speech-audio-devices", 0).edit().clear().commit(); shadow.setInputDevices(emptyList()); shadow.setOutputDevices(emptyList()) }
    private fun stream(changes: MutableList<AudioDeviceInfo?>): AudioRouting = Proxy.newProxyInstance(
        AudioRouting::class.java.classLoader, arrayOf(AudioRouting::class.java)) { proxy, method, args ->
        when (method.name) {
            "setPreferredDevice" -> { changes += args?.firstOrNull() as? AudioDeviceInfo; true }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> null
        }
    } as AudioRouting
    @Test fun inputAndOutputRemainIndependentAndReconnectRestoresTheSavedRoute() {
        val mic = device(AudioDeviceInfo.TYPE_BUILTIN_MIC); val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        shadow.setInputDevices(listOf(mic)); shadow.setOutputDevices(listOf(speaker))
        val inputs = mutableListOf<AudioDeviceInfo?>(); val outputs = mutableListOf<AudioDeviceInfo?>()
        val input = stream(inputs); val output = stream(outputs)
        AudioDeviceRouting(app).use { routes ->
            routes.bind(input, true); routes.bind(output, false)
            routes.select(true, routes.endpoints(true).single().key); shadowOf(Looper.getMainLooper()).idle()
            assertSame(mic, inputs.last()); assertNull(outputs.last())
            val saved = routes.endpoints(false).single().key
            routes.select(false, saved); shadowOf(Looper.getMainLooper()).idle(); assertSame(speaker, outputs.last())
            shadow.removeOutputDevice(speaker, true); shadowOf(Looper.getMainLooper()).idle()
            assertNull(outputs.last()); assertEquals(saved, routes.selected(false)); assertTrue(routes.summary(false).contains("未连接"))
            val reconnected = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            shadow.addOutputDevice(reconnected, true); shadowOf(Looper.getMainLooper()).idle(); assertSame(reconnected, outputs.last())
            routes.unbind(output); val previous = outputs.size
            routes.select(false, null); shadowOf(Looper.getMainLooper()).idle(); assertEquals(previous, outputs.size)
        }
        AudioDeviceRouting(app).use { assertEquals("内置麦克风", it.summary(true)); assertEquals("系统默认", it.summary(false)) }
    }
    @Test fun servicePagesExposeOnlyTheirOwnRouteAndProvidersCannotInvokeHostSelection() {
        fun plugin(role: TtsRole) = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.audio", "test", "test")
            override val tts = object : TtsEngine {
                override val info = TtsInfo(role = role)
                override fun synthesize(request: TtsRequest) = kotlinx.coroutines.flow.emptyFlow<SynthesisEvent>()
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.NoAction
        }
        AudioDeviceRouting(app).use { routes ->
            val page = PluginPage("manager", "语音合成服务", emptyList())
            assertEquals(page, routes.decorate(plugin(TtsRole.PROVIDER), page))
            val group = routes.decorate(plugin(TtsRole.SERVICE), page)!!.blocks.single() as PageBlock.Group
            assertEquals("首选朗读设备", group.children.filterIsInstance<PageBlock.Select>().single().title)
            assertEquals(SPEECH_GAIN_ACTION, group.children.filterIsInstance<PageBlock.Slider>().single().control.actionId)
            try { routes.handle(plugin(TtsRole.PROVIDER), "host-audio/output/default"); fail() } catch (_: IllegalArgumentException) { }
            assertTrue(routes.handle(plugin(TtsRole.SERVICE), "host-audio/output/default"))
            assertTrue(routes.handle(plugin(TtsRole.SERVICE), SPEECH_GAIN_ACTION, mapOf("value" to "4.5")))
            assertEquals(4.5f, routes.outputGain)
            for (invalid in listOf("NaN", "Infinity", "-1", "11")) {
                try { routes.handle(plugin(TtsRole.SERVICE), SPEECH_GAIN_ACTION, mapOf("value" to invalid)); fail() } catch (_: IllegalArgumentException) { }
            }
            assertEquals(4.5f, routes.outputGain)
        }
        AudioDeviceRouting(app).use { assertEquals(4.5f, it.outputGain) }
    }
    @Test fun systemNoiseDefaultsOffAndIsOnlyOnRecognitionService() {
        fun plugin(role: AsrRole) = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.asr", "test", "test")
            override val asr = object : AsrEngine {
                override val info = AsrInfo(role = role)
                override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = RecognitionResult("")
                override fun recognizeStream(audio: kotlinx.coroutines.flow.Flow<PcmAudio>, config: RecognitionConfig) =
                    kotlinx.coroutines.flow.emptyFlow<RecognitionEvent>()
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.NoAction
        }
        val page = PluginPage("manager", "语音识别", emptyList())
        AudioDeviceRouting(app).use { routes ->
            assertFalse(routes.noiseSuppression)
            assertEquals(page, routes.decorate(plugin(AsrRole.PROVIDER), page))
            val group = routes.decorate(plugin(AsrRole.SERVICE), page)!!.blocks.single() as PageBlock.Group
            val toggle = group.children.filterIsInstance<PageBlock.Toggle>().single()
            assertEquals("系统降噪", toggle.title)
            assertFalse(toggle.checked)
            assertTrue(routes.handle(plugin(AsrRole.SERVICE), toggle.actionId, mapOf("checked" to "false")))
            try { routes.handle(plugin(AsrRole.PROVIDER), toggle.actionId, mapOf("checked" to "false")); fail() }
            catch (_: IllegalArgumentException) { }
        }
    }
}
