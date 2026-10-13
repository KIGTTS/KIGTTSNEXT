package com.kigtts.next

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/** Exercises actual service -> host broker -> independent Piper DEX/JNI -> PCM. */
@RunWith(AndroidJUnit4::class)
class SpeechServiceDeviceTest {
    @Test fun builtInFallbackAndManagedPiperVoicePackWorkWithoutStandaloneSystemPlugin() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = PluginManager(instrumentation.targetContext, emptyList())
        val args = InstrumentationRegistry.getArguments()
        val before = manager.packages.installed().associate { it.manifest.id to it.enabled }
        val added = mutableListOf<String>()
        val keep = args.getString("keepPackages") == "true"
        val voiceArchive = requireNotNull(args.getString("piperVoiceZip")) { "请提供 Piper 测试语音包的设备绝对路径" }
        val serviceId = "service.speech-synthesis"
        try {
            if ("engine.system-tts" in before) manager.packages.enabled("engine.system-tts", false)
            for (name in listOf("engine.sensevoice", "engine.piper", serviceId)) {
                val filename = "$name-0.8.0.kigplugin"
                val input = File(manager.paths.packages, ".speech-test-$filename")
                instrumentation.context.assets.open(filename).use { source -> input.outputStream().use { source.copyTo(it) } }
                try {
                    val preview = manager.packages.inspect(input)
                    if (manager.packages.installed().firstOrNull { it.manifest.id == name }?.manifest?.versionCode != preview.manifest.versionCode) {
                        manager.install(input, preview.publisherSha256)
                        if (name !in before) added += name
                    }
                } finally { input.delete() }
            }
            for (id in listOf("engine.sensevoice", "engine.piper", serviceId)) manager.enable(id, true)
            if ("engine.system-tts" in before) manager.packages.enabled("engine.system-tts", false)
            manager.reload()
            assertTrue(manager.status.toString(), manager.status.all { it.error == null })
            assertNull(manager.registry.plugin("engine.system-tts"))
            var service = requireNotNull(manager.registry.plugin(serviceId))
            assertEquals(TtsRole.SERVICE, service.tts!!.info.role)
            service.execute("system", PluginContext("", ""))
            val fallback = service.tts!!.synthesize(TtsRequest("你好，这是内置系统语音测试。")).toList()
            verifyAndPlay(fallback, "Built-in system fallback", args.getString("playAudio") == "true")
            voiceArchive.let { archive ->
                val importedPath = manager.importResource(serviceId, "resources/inbox", "test-piper.kigvpk", false) { File(archive).inputStream() }
                service = requireNotNull(manager.registry.plugin(serviceId))
                service.execute("imported/${encode("engine.piper")}", PluginContext("", "", inputs = mapOf("resourcePath" to importedPath)))
                val piper = requireNotNull(manager.registry.plugin("engine.piper"))
                assertEquals(EngineAvailability.READY, piper.tts!!.info.availability)
                val voice = piper.tts!!.info.selectedVoiceId ?: piper.tts!!.info.voices.first().id
                service.execute("voice/${encode("engine.piper")}/${encode(voice)}", PluginContext("", ""))
                val audio = service.tts!!.synthesize(TtsRequest("This is the Piper plugin speaking.", language = "en-US")).toList()
                verifyAndPlay(audio, "Managed Piper native synthesis", args.getString("playAudio") == "true")
                service.execute("renamed/${encode("engine.piper")}/${encode(voice)}", PluginContext("", "", inputs = mapOf("value" to "Piper 测试语音")))
                assertTrue(piper.tts!!.info.voices.any { it.name.startsWith("Piper 测试语音") })
                assertTrue(service.execute("delete/${encode("engine.piper")}/${encode(voice)}", PluginContext("", "")) is PluginOutcome.Menu)
                val cancellation = launch { service.tts!!.synthesize(TtsRequest("Cancel this inference request.", language = "en-US")).toList() }
                delay(20); cancellation.cancelAndJoin()
                verifyAndPlay(service.tts!!.synthesize(TtsRequest("Piper works after cancellation.", language = "en-US")).toList(), "Piper after cancel", false)
                manager.enable("engine.piper", false)
                verifyAndPlay(service.tts!!.synthesize(TtsRequest("插件停用后使用系统语音。")).toList(), "Disabled provider fallback", false)
                manager.enable("engine.piper", true)
            }
            android.util.Log.i("KigSpeechServiceTest", "Speech service, scoped management and fallback checks completed")
        } finally {
            if (!keep) {
                for (id in added) manager.uninstall(id)
                for ((id, enabled) in before) manager.enable(id, enabled)
            }
            manager.close()
        }
    }
    private suspend fun verifyAndPlay(events: List<SynthesisEvent>, label: String, play: Boolean) {
        val chunks = events.filterIsInstance<SynthesisEvent.Audio>()
        val bytes = chunks.sumOf { it.audio.bytes.size }
        assertTrue("$label produced no PCM", bytes > 0)
        assertTrue(events.last() is SynthesisEvent.Finished)
        android.util.Log.i("KigSpeechServiceTest", "$label: $bytes bytes PCM")
        if (play) {
            val player = com.kigtts.next.audio.AndroidAudioPlatform(InstrumentationRegistry.getInstrumentation().targetContext).player()
            try { chunks.forEach { player.write(it.audio) }; player.finish() } finally { player.close() }
        }
    }
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
