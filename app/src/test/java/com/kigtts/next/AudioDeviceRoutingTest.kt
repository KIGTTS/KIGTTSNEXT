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
@Config(sdk = [35])
class AudioDeviceRoutingTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(AudioManager::class.java)
    private val shadow get() = shadowOf(manager)
    private fun device(type: Int) = AudioDeviceInfoBuilder.newBuilder().setType(type).build().also {
        // Robolectric's public builder only maps output device types.
        if (type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            val port: Any = org.robolectric.util.ReflectionHelpers.getField(it, "mPort")
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
            assertEquals("首选朗读设备", (group.children.single() as PageBlock.Select).title)
            try { routes.handle(plugin(TtsRole.PROVIDER), "host-audio/output/default"); fail() } catch (_: IllegalArgumentException) { }
            assertTrue(routes.handle(plugin(TtsRole.SERVICE), "host-audio/output/default"))
        }
    }
}
