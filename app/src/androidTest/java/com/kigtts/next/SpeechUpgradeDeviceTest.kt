package com.kigtts.next

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Reuses existing model data after the API3 package update; restores the everyday system voice. */
@RunWith(AndroidJUnit4::class)
class SpeechUpgradeDeviceTest {
    @Test fun recognitionSurvivesPackageUpdateAndServiceUsesBuiltInSystemByDefault() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = PluginManager(instrumentation.targetContext, emptyList())
        try {
            manager.reload()
            assertTrue(manager.status.toString(), manager.status.all { it.error == null })
            assertNull(manager.registry.plugin("engine.system-tts"))
            val plugin = requireNotNull(manager.registry.plugin("engine.sensevoice"))
            val engine = requireNotNull(plugin.asr)
            assertEquals(EngineAvailability.READY, engine.info.availability)
            val sample = File(requireNotNull(InstrumentationRegistry.getArguments().getString("recognitionWav"))).canonicalFile
            require(sample.toPath().startsWith(manager.paths.storage("engine.sensevoice").root.canonicalFile.toPath()))
            val pcm = wavPcm(sample)
            val batch = engine.recognize(pcm)
            assertTrue(batch.text.isNotBlank())
            Log.i("KigSpeechUpgradeTest", "API3 retained-model batch: ${batch.text}")
            val chunks = flow {
                for (offset in pcm.bytes.indices step 640) emit(PcmAudio(pcm.bytes.copyOfRange(offset, minOf(offset + 640, pcm.bytes.size))))
                repeat(50) { emit(PcmAudio(ByteArray(640))) }
            }
            val streamed = engine.recognizeStream(chunks).toList().filterIsInstance<RecognitionEvent.Final>()
            assertTrue(streamed.any { it.result.text.isNotBlank() })
            Log.i("KigSpeechUpgradeTest", "API3 segmented: ${streamed.joinToString { it.result.text }}")
            val service = requireNotNull(manager.registry.plugin("service.speech-synthesis"))
            service.execute("system", PluginContext("", ""))
            assertEquals(TtsRole.SERVICE, service.tts!!.info.role)
            Log.i("KigSpeechUpgradeTest", "Piper retained; standalone system disabled; service default restored to built-in system TTS")
        } finally { manager.close() }
    }
}
