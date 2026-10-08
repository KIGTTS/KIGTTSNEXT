package com.kigtts.next

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real DEX/JNI/IPC verification. Test APK assets never enter the production APK. */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeDeviceTest {
    @Test fun independentEnginePackagesLoadInWorkersAndNativeRuntimeResolves() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val manager = PluginManager(target, emptyList())
        val existing = manager.packages.installed().map { it.manifest.id }.toSet()
        val added = mutableListOf<String>()
        try {
            for (name in listOf("engine.system-tts-0.1.0.kigplugin", "engine.sensevoice-0.1.0.kigplugin")) {
                val input = File(manager.paths.packages, ".device-test-$name")
                instrumentation.context.assets.open(name).use { source -> input.outputStream().use { source.copyTo(it) } }
                try {
                    val preview = manager.packages.inspect(input)
                    if (preview.manifest.id !in existing) {
                        manager.install(input, preview.publisherSha256); added += preview.manifest.id
                    }
                } finally { input.delete() }
            }
            manager.reload()
            assertTrue(manager.status.all { it.error == null })
            val asr = requireNotNull(manager.registry.plugin("engine.sensevoice"))
            assertEquals(StreamingKind.SEGMENTED, asr.asr!!.info.streaming)
            if ("engine.sensevoice" in added) assertEquals(EngineAvailability.NEEDS_RESOURCES, asr.asr!!.info.availability)
            val probe = asr.execute("verify-runtime", PluginContext("", "")) as PluginOutcome.Message
            assertEquals("推理运行库已加载", probe.text)
            android.util.Log.i("KigPluginDeviceTest", "DEX + sherpa JNI + ONNX Runtime loaded through external package")
            val tts = requireNotNull(manager.registry.plugin("engine.system-tts"))
            val empty = tts.tts!!.synthesize(TtsRequest("")).toList()
            assertTrue(empty.last() is SynthesisEvent.Finished)
            val audio = tts.tts!!.synthesize(TtsRequest("你好，这是插件语音测试。")).toList()
            val count = audio.filterIsInstance<SynthesisEvent.Audio>().sumOf { it.audio.bytes.size }
            assertTrue("System TTS produced no PCM", count > 0)
            android.util.Log.i("KigPluginDeviceTest", "System TTS synthesized $count PCM bytes through IPC")
            manager.enable("engine.system-tts", false)
            assertNull(manager.registry.plugin("engine.system-tts"))
            manager.enable("engine.system-tts", true)
            assertNotNull(manager.registry.plugin("engine.system-tts"))
        } finally {
            // Preserve pre-existing plugins/data; remove only packages created by this test.
            for (id in added) manager.uninstall(id)
            manager.close()
        }
    }
}
