package com.kigtts.next

import android.util.Log
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*

/** Deterministic WAV fixtures test inference without recording the user's surroundings. */
internal suspend fun recognitionDeviceChecks(manager: PluginManager, archivePath: String, wavPath: String) {
    val id = "engine.sensevoice"
    val storage = manager.paths.storage(id)
    val archive = File(archivePath).canonicalFile
    val wav = File(wavPath).canonicalFile
    require(archive.toPath().startsWith(storage.root.canonicalFile.toPath()))
    require(wav.toPath().startsWith(storage.root.canonicalFile.toPath()))
    manager.importResource(id, "models", "model-test.zip", true) { archive.inputStream() }
    val engine = requireNotNull(manager.registry.plugin(id)?.asr)
    assertEquals(EngineAvailability.READY, engine.info.availability)
    val pcm = wavPcm(wav)
    val start = android.os.SystemClock.elapsedRealtime()
    val batch = engine.recognize(pcm)
    assertTrue("ASR returned an empty result", batch.text.isNotBlank())
    Log.i("KigPluginDeviceTest", "ASR batch (${android.os.SystemClock.elapsedRealtime() - start} ms): ${batch.text}")
    val chunks = flow {
        for (offset in pcm.bytes.indices step 640) emit(PcmAudio(pcm.bytes.copyOfRange(offset, minOf(offset + 640, pcm.bytes.size))))
        repeat(50) { emit(PcmAudio(ByteArray(640))) }
    }
    val streamed = engine.recognizeStream(chunks).toList().filterIsInstance<RecognitionEvent.Final>()
    assertTrue("Segmented stream returned no utterances", streamed.any { it.result.text.isNotBlank() })
    Log.i("KigPluginDeviceTest", "ASR segmented: ${streamed.joinToString { it.result.text }}")
    withTimeout(5_000) {
        engine.recognizeStream(flow { emit(PcmAudio(ByteArray(640))); awaitCancellation() }).first()
    }
    assertTrue(engine.recognize(pcm).text.isNotBlank())
    Log.i("KigPluginDeviceTest", "ASR stream cancellation released the session; subsequent batch succeeded")
    assertTrue(storage.temporary.listFiles().orEmpty().none { it.name.startsWith("import-") })
}

private fun wavPcm(file: File): PcmAudio {
    val bytes = file.readBytes()
    require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE")
    val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var rate = 0; var channels = 0; var offset = 12; var audio: ByteArray? = null
    while (offset + 8 <= bytes.size) {
        val length = input.getInt(offset + 4)
        require(length >= 0 && offset.toLong() + 8 + length <= bytes.size)
        val start = offset + 8
        when (String(bytes, offset, 4)) {
            "fmt " -> {
                require(length >= 16 && input.getShort(start).toInt() == 1 && input.getShort(start + 14).toInt() == 16)
                channels = input.getShort(start + 2).toInt(); rate = input.getInt(start + 4)
            }
            "data" -> audio = bytes.copyOfRange(start, start + length)
        }
        offset = start + length + (length and 1)
    }
    require(rate == 16_000 && channels == 1)
    return PcmAudio(requireNotNull(audio), rate, channels)
}
