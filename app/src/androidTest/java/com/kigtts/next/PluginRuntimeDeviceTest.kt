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
    @Test fun independentEnginePackagesLoadInWorkersAndNativeRuntimeResolves() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val manager = PluginManager(target, emptyList())
        val existing = manager.packages.installed().map { it.manifest.id }.toSet()
        val keepPackages = InstrumentationRegistry.getArguments().getString("keepPackages") == "true"
        val added = mutableListOf<String>()
        try {
            for (name in listOf("service.speech-synthesis-0.8.0.kigplugin", "engine.sensevoice-0.8.0.kigplugin")) {
                val input = File(manager.paths.packages, ".device-test-$name")
                instrumentation.context.assets.open(name).use { source -> input.outputStream().use { source.copyTo(it) } }
                try {
                    val preview = manager.packages.inspect(input)
                    if (manager.packages.installed().firstOrNull { it.manifest.id == preview.manifest.id }?.manifest?.versionCode != preview.manifest.versionCode) {
                        manager.install(input, preview.publisherSha256)
                        if (preview.manifest.id !in existing) added += preview.manifest.id
                    }
                } finally { input.delete() }
            }
            for (id in listOf("service.speech-synthesis", "engine.sensevoice")) manager.enable(id, true)
            manager.reload()
            assertTrue(manager.status.toString(), manager.status.all { it.error == null })
            val asr = requireNotNull(manager.registry.plugin("engine.sensevoice"))
            assertEquals(StreamingKind.SEGMENTED, asr.asr!!.info.streaming)
            if ("engine.sensevoice" in added) assertEquals(EngineAvailability.NEEDS_RESOURCES, asr.asr!!.info.availability)
            val probe = asr.execute("verify-runtime", PluginContext("", "")) as PluginOutcome.Message
            assertEquals("推理运行库已加载", probe.text)
            android.util.Log.i("KigPluginDeviceTest", "DEX + sherpa JNI + ONNX Runtime loaded through external package")
            val tts = requireNotNull(manager.registry.plugin("service.speech-synthesis"))
            tts.execute("system", PluginContext("", ""))
            val empty = tts.tts!!.synthesize(TtsRequest("")).toList()
            assertTrue(empty.last() is SynthesisEvent.Finished)
            val audio = tts.tts!!.synthesize(TtsRequest("你好，这是插件语音测试。")).toList()
            val count = audio.filterIsInstance<SynthesisEvent.Audio>().sumOf { it.audio.bytes.size }
            assertTrue("System TTS produced no PCM", count > 0)
            android.util.Log.i("KigPluginDeviceTest", "System TTS synthesized $count PCM bytes through IPC")
            if (InstrumentationRegistry.getArguments().getString("playAudio") == "true") {
                val player = com.kigtts.next.audio.AndroidAudioPlatform(target).player()
                try {
                    audio.filterIsInstance<SynthesisEvent.Audio>().forEach { player.write(it.audio) }
                    player.finish()
                } finally { player.close() }
                android.util.Log.i("KigPluginDeviceTest", "PCM playback completed through host AudioTrack")
            }
            manager.enable("service.speech-synthesis", false)
            assertNull(manager.registry.plugin("service.speech-synthesis"))
            manager.enable("service.speech-synthesis", true)
            assertTrue(manager.status.toString(), manager.status.all { it.error == null })
            assertNotNull(manager.registry.plugin("service.speech-synthesis"))
            val arguments = InstrumentationRegistry.getArguments()
            arguments.getString("resourceZip")?.let { archive ->
                recognitionDeviceChecks(manager, archive, requireNotNull(arguments.getString("sampleWav")))
            }
        } finally {
            // Preserve pre-existing plugins/data; remove only packages created by this test.
            if (!keepPackages) for (id in added) manager.uninstall(id)
            manager.close()
        }
    }
}
