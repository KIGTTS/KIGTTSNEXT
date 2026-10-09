package com.kigtts.next

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.util.Log
import java.io.File
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual UI-service-provider protocol, packaged online JNI, pre-EOF partials and cancellation. */
@RunWith(AndroidJUnit4::class)
class StreamingRecognitionDeviceTest {
    @Test fun onlineLanguagePacksProduceRealPartialsBeforeInputCompletion() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments(); val id = "engine.streaming-asr"
        val serviceId = "service.speech-recognition"
        val manager = PluginManager(instrumentation.targetContext, emptyList())
        try {
            for (packageId in listOf("engine.system-tts", "engine.sensevoice", "engine.piper", "service.speech-synthesis", id, serviceId)) {
                val input = File(manager.paths.packages, ".recognition-test-$packageId.kigplugin")
                instrumentation.context.assets.open("$packageId-0.5.0.kigplugin").use { source -> input.outputStream().use { source.copyTo(it) } }
                try {
                    val preview = manager.packages.inspect(input)
                    if (manager.packages.installed().none { it.manifest.id == packageId && it.manifest.versionCode == preview.manifest.versionCode }) manager.install(input, preview.publisherSha256)
                } finally { input.delete() }
            }
            manager.enable(id, true); manager.enable(serviceId, true); manager.reload()
            assertTrue(manager.status.toString(), manager.status.all { it.error == null })
            var plugin = requireNotNull(manager.registry.plugin(id))
            plugin.execute("filter/all", PluginContext("", ""))
            val storage = manager.paths.storage(id)
            for (language in listOf("zh", "en")) {
                val archive = File(requireNotNull(args.getString("${language}Pack"))).canonicalFile
                val wav = File(requireNotNull(args.getString("${language}Wav"))).canonicalFile
                require(archive.toPath().startsWith(storage.root.toPath()) && wav.toPath().startsWith(storage.root.toPath()))
                val before = plugin.page("packs", PluginContext("", ""))!!.management!!.items.map { it.id }
                manager.importResource(id, "resources/inbox", "$language.kigasr", false) { archive.inputStream() }
                // Resource import retires/reloads the worker; never retain its old proxy.
                plugin = requireNotNull(manager.registry.plugin(id))
                plugin.execute("imported", PluginContext("", "", inputs = mapOf("resourcePath" to "resources/inbox/$language.kigasr")))
                val page = plugin.page("packs", PluginContext("", ""))!!.management!!
                val imported = page.items.first { it.id !in before }
                plugin.execute("use/${imported.id}", PluginContext("", ""))
                val service = requireNotNull(manager.registry.plugin(serviceId))
                service.execute("select/$id", PluginContext("", ""))
                val engine = requireNotNull(service.asr)
                assertEquals(AsrRole.SERVICE, engine.info.role)
                assertEquals(AsrRole.PROVIDER, plugin.asr!!.info.role)
                assertEquals(StreamingKind.INCREMENTAL, engine.info.streaming)
                val pcm = wavPcm(wav); val eof = java.util.concurrent.atomic.AtomicBoolean(false)
                var partials = 0; var preEofPartials = 0; val finals = mutableListOf<String>()
                withTimeout(90_000) {
                    engine.recognizeStream(flow {
                        for (offset in pcm.bytes.indices step 3200) { emit(PcmAudio(pcm.bytes.copyOfRange(offset, minOf(offset + 3200, pcm.bytes.size)))); delay(25) }
                        delay(100); eof.set(true)
                    }).collect { event ->
                        if (event is RecognitionEvent.Partial) { assertTrue(event.text.isNotBlank()); partials++; if (!eof.get()) preEofPartials++ }
                        if (event is RecognitionEvent.Final) finals += event.result.text
                    }
                }
                assertTrue("No real incremental result before EOF for $language", preEofPartials > 0); assertTrue(finals.any { it.isNotBlank() })
                withTimeout(30_000) {
                    engine.recognizeStream(flow { for (offset in pcm.bytes.indices step 3200) emit(PcmAudio(pcm.bytes.copyOfRange(offset, minOf(offset + 3200, pcm.bytes.size)))); awaitCancellation() })
                        .first { it is RecognitionEvent.Partial }
                }
                assertTrue(engine.recognize(pcm).text.isNotBlank())
                Log.i("KigStreamingDeviceTest", "$language service->$id partials=$partials finals=${finals.joinToString()}; cancellation/reuse OK")
            }
            assertTrue(plugin.page("packs", PluginContext("", ""))!!.management!!.items.size >= 2)
        } finally { manager.close() }
    }
}
