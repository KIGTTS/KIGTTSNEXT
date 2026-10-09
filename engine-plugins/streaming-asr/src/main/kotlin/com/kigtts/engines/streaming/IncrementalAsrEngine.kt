package com.kigtts.engines.streaming

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

/** Decode every available online chunk. Partial text always comes from the decoder's current hypothesis. */
internal class IncrementalAsrEngine(private val packs: RecognitionPackStore,
    private val create: (RecognitionPack, Int, Boolean) -> OnlineSession = ::SherpaOnlineSession) : AsrEngine {
    override val info get() = AsrInfo(inputFormat = AudioFormat(packs.selected()?.sampleRate ?: 16_000),
        streaming = StreamingKind.INCREMENTAL, availability = if (packs.selected() != null) EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES)
    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = flow {
        packs.session.withLock {
            val pack = selected()
            val decoder = initialize(pack, true)
            try {
                emit(RecognitionEvent.Started); var previous = ""; var received = false
                suspend fun drain() { while (decoder.ready()) { currentCoroutineContext().ensureActive(); decoder.decode() } }
                audio.collect { chunk ->
                    validate(chunk, pack); currentCoroutineContext().ensureActive()
                    val input = samples(chunk)
                    for (offset in input.indices step (pack.sampleRate / 10)) {
                        received = true
                        decoder.accept(input.copyOfRange(offset, minOf(offset + pack.sampleRate / 10, input.size))); drain()
                        if (decoder.endpoint()) {
                            if (pack.architecture == OnlineArchitecture.PARAFORMER) { decoder.accept(FloatArray(pack.sampleRate * 8 / 10)); drain() }
                            val text = decoder.text()
                            if (text.isNotBlank()) emit(RecognitionEvent.Final(RecognitionResult(text)))
                            decoder.reset(); previous = ""
                        } else {
                            val text = decoder.text()
                            if (text.isNotBlank() && text != previous) { emit(RecognitionEvent.Partial(text)); previous = text }
                        }
                    }
                }
                if (received) {
                    decoder.accept(FloatArray(pack.sampleRate * (if (pack.architecture == OnlineArchitecture.PARAFORMER) 8 else 3) / 10))
                    decoder.finishInput(); drain()
                    val text = decoder.text(); if (text.isNotBlank()) emit(RecognitionEvent.Final(RecognitionResult(text)))
                }
            } finally { decoder.close() }
        }
    }.flowOn(Dispatchers.IO)
    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult = withContext(Dispatchers.IO) {
        packs.session.withLock {
            val pack = selected(); validate(audio, pack)
            require(audio.bytes.size <= pack.sampleRate * 2 * info.maxBatchSeconds) { "完整录音最多 ${info.maxBatchSeconds} 秒" }
            if (audio.bytes.isEmpty()) return@withLock RecognitionResult("")
            val decoder = initialize(pack, false)
            try {
                val input = samples(audio)
                for (offset in input.indices step (pack.sampleRate / 10)) {
                    currentCoroutineContext().ensureActive(); decoder.accept(input.copyOfRange(offset, minOf(offset + pack.sampleRate / 10, input.size)))
                    while (decoder.ready()) { currentCoroutineContext().ensureActive(); decoder.decode() }
                }
                decoder.accept(FloatArray(pack.sampleRate * (if (pack.architecture == OnlineArchitecture.PARAFORMER) 8 else 3) / 10))
                decoder.finishInput()
                while (decoder.ready()) { currentCoroutineContext().ensureActive(); decoder.decode() }
                RecognitionResult(decoder.text())
            } finally { decoder.close() }
        }
    }
    private fun selected() = packs.selected() ?: throw EngineException(EngineError.NOT_CONFIGURED, "请先导入并选用一个流式识别包")
    private fun initialize(pack: RecognitionPack, endpoints: Boolean): OnlineSession = try { create(pack, packs.threads, endpoints) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw EngineException(EngineError.INITIALIZATION, "识别包无法用于在线解码：${error.message}", error) }
        catch (error: LinkageError) { throw EngineException(EngineError.INITIALIZATION, "流式识别运行库加载失败", error) }
    private fun validate(audio: PcmAudio, pack: RecognitionPack) {
        if (audio.sampleRate != pack.sampleRate || audio.channels != 1) throw EngineException(EngineError.UNSUPPORTED_FORMAT, "需要 ${pack.sampleRate} Hz 单声道 PCM16")
    }
    private fun samples(audio: PcmAudio) = FloatArray(audio.bytes.size / 2) { index ->
        (((audio.bytes[index * 2].toInt() and 255) or (audio.bytes[index * 2 + 1].toInt() shl 8)).toShort().toFloat()) / 32768f
    }
}
