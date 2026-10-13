package com.kigtts.plugin.runtime

import android.os.ParcelFileDescriptor
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/** Shared service/provider transport. EOF is explicit in both directions; cancellation closes all descriptors. */
internal fun recognitionPipe(audio: Flow<PcmAudio>, config: RecognitionConfig, streaming: Boolean,
    open: (ParcelFileDescriptor, Boolean, String, String) -> ParcelFileDescriptor, cancel: (String) -> Unit) = channelFlow {
    val token = UUID.randomUUID().toString(); val input = ParcelFileDescriptor.createPipe()
    val output = try { open(input[0], streaming, PluginJson.encodeToString(config), token) }
        catch (error: Exception) { input.forEach { it.close() }; throw error }
    input[0].close()
    val writer = launch(Dispatchers.IO) {
        try {
            DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(input[1])).use { data ->
                audio.collect { AudioWire.pcm(data, it) }; data.writeInt(AudioWire.END_INPUT); data.flush()
            }
        } catch (error: IOException) {
            if (isActive) throw error else throw CancellationException("识别输入已结束").apply { initCause(error) }
        }
    }
    val unblock = launch(start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally {
            runCatching { output.close() }; runCatching { input[1].close() }; runCatching { cancel(token) }
        }
    }
    try {
        withContext(Dispatchers.IO) {
            DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(output)).use { data ->
                readRecognitionResponses(data) { send(it) }
            }
        }
    } finally { writer.cancel(); unblock.cancelAndJoin(); writer.cancelAndJoin() }
}.flowOn(Dispatchers.IO)

internal suspend fun readRecognitionResponses(input: DataInputStream, emit: suspend (RecognitionEvent) -> Unit) {
    while (true) when (AudioWire.tag(input)) {
        null -> error("识别进程中断")
        AudioWire.START -> emit(RecognitionEvent.Started)
        AudioWire.PARTIAL -> emit(RecognitionEvent.Partial(AudioWire.readText(input)))
        AudioWire.FINAL -> emit(RecognitionEvent.Final(PluginJson.decodeFromString(AudioWire.readText(input))))
        AudioWire.TRANSCRIPT -> emit(PluginJson.decodeFromString<RecognitionEvent.Transcript>(AudioWire.readText(input)))
        AudioWire.FINISH -> return
        AudioWire.ERROR -> AudioWire.failure(input)
        else -> error("非法识别响应")
    }
}

internal suspend fun transferRecognition(input: ParcelFileDescriptor, output: DataOutputStream, engine: AsrEngine,
    streaming: Boolean, config: RecognitionConfig) {
    DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(input)).use { data ->
        val chunks = flow {
            while (true) {
                currentCoroutineContext().ensureActive()
                val tag = AudioWire.tag(data) ?: error("录音流意外中断")
                if (tag == AudioWire.END_INPUT) break
                require(tag == AudioWire.AUDIO); emit(AudioWire.readPcm(data))
            }
        }
        if (streaming) {
            require(engine.info.streaming != StreamingKind.NONE)
            engine.recognizeStream(chunks, config).collect { event -> when (event) {
                RecognitionEvent.Started -> { output.writeInt(AudioWire.START); output.flush() }
                is RecognitionEvent.Partial -> AudioWire.text(output, AudioWire.PARTIAL, event.text)
                is RecognitionEvent.Final -> AudioWire.text(output, AudioWire.FINAL, PluginJson.encodeToString(event.result))
                is RecognitionEvent.Transcript -> AudioWire.text(output, AudioWire.TRANSCRIPT, PluginJson.encodeToString(event))
            } }
        } else {
            require(engine.info.batch)
            val bytes = java.io.ByteArrayOutputStream(); var format: AudioFormat? = null
            chunks.collect { chunk ->
                val current = AudioFormat(chunk.sampleRate, chunk.channels)
                require(format == null || format == current); format = current
                require(bytes.size().toLong() + chunk.bytes.size <= current.sampleRate.toLong() * current.channels * 2 * engine.info.maxBatchSeconds)
                bytes.write(chunk.bytes)
            }
            val pcm = format ?: engine.info.inputFormat
            AudioWire.text(output, AudioWire.FINAL, PluginJson.encodeToString(engine.recognize(PcmAudio(bytes.toByteArray(), pcm.sampleRate, pcm.channels), config)))
        }
    }
    output.writeInt(AudioWire.FINISH); output.flush()
}
