// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.system

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kigtts.plugin.android.AndroidPluginEnvironment
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.UUID

/** Port of KIGTTS Engines.kt SystemTtsEngine, with cancellable waits and plugin-only temp storage. */
class SystemSpeechEngine(private val environment: AndroidPluginEnvironment,
    private val settings: () -> Pair<String, Float>) : TtsEngine {
    private val session = Mutex()
    private var availableVoices = emptyList<VoiceInfo>()
    override val info get() = TtsInfo(voices = availableVoices)
    suspend fun refreshVoices(): List<VoiceInfo> {
        val engine = initialize()
        try {
            availableVoices = engine.voices.orEmpty().sortedBy { it.name }.take(500)
                .map { VoiceInfo(it.name, it.name, it.locale.toLanguageTag(), if (it.isNetworkConnectionRequired) "需要网络" else "本地声音") }
            return availableVoices
        } finally { withContext(NonCancellable + Dispatchers.Main) { engine.shutdown() } }
    }
    override fun synthesize(request: TtsRequest) = flow {
        session.withLock {
            emit(SynthesisEvent.Started)
            if (request.text.isBlank()) { emit(SynthesisEvent.Finished()); return@withLock }
            require(request.text.length <= info.maxTextLength) { "单次朗读文本过长" }
            val engine = initialize()
            val file = File(environment.storage.temporary, "system-tts-${UUID.randomUUID()}.wav")
            try {
                val (language, rate) = settings()
                val languageStatus = engine.setLanguage(Locale.forLanguageTag(language))
                if (languageStatus < 0) throw EngineException(EngineError.NOT_CONFIGURED, "系统 TTS 未安装 $language 的语音数据")
                request.voiceId?.let { id -> engine.voices.firstOrNull { it.name == id }?.let { engine.voice = it } }
                engine.setSpeechRate((rate * request.rate).coerceIn(0.25f, 4f))
                val completed = CompletableDeferred<Unit>()
                val token = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId == token) completed.complete(Unit) }
                    @Deprecated("Platform callback") override fun onError(utteranceId: String?) {
                        if (utteranceId == token) completed.completeExceptionally(EngineException(EngineError.INFERENCE, "系统 TTS 合成失败"))
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) { onError(utteranceId) }
                })
                if (engine.synthesizeToFile(request.text, Bundle(), file, token) != TextToSpeech.SUCCESS)
                    throw EngineException(EngineError.INFERENCE, "系统 TTS 拒绝合成请求")
                try { withTimeout(30_000) { completed.await() } }
                catch (timeout: TimeoutCancellationException) { throw EngineException(EngineError.TIMEOUT, "系统 TTS 合成超时", timeout) }
                val audio = WavPcm.read(file)
                val stride = 2 * audio.channels
                val chunkSize = 32_768 / stride * stride
                for (offset in audio.bytes.indices step chunkSize) {
                    currentCoroutineContext().ensureActive()
                    emit(SynthesisEvent.Audio(PcmAudio(audio.bytes.copyOfRange(offset, minOf(offset + chunkSize, audio.bytes.size)), audio.sampleRate, audio.channels)))
                }
                emit(SynthesisEvent.Finished())
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { engine.stop(); engine.shutdown() }
                file.delete()
            }
        }
    }
    private suspend fun initialize(): TextToSpeech {
        @Suppress("DEPRECATION")
        val packages = environment.context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo?.packageName }.distinct().take(10)
        for (name in listOf<String?>(null) + packages) {
            var candidate: TextToSpeech? = null
            try {
                val initialized = CompletableDeferred<Int>()
                withContext(Dispatchers.Main) {
                    candidate = TextToSpeech(environment.context, { initialized.complete(it) }, name)
                }
                if (withTimeout(7_000) { initialized.await() } == TextToSpeech.SUCCESS) return requireNotNull(candidate)
            } catch (_: TimeoutCancellationException) {
                // A service that never calls back must not block all fallback engines.
            } catch (cancel: CancellationException) {
                withContext(NonCancellable + Dispatchers.Main) { candidate?.shutdown() }; throw cancel
            } catch (_: Exception) { }
            withContext(NonCancellable + Dispatchers.Main) { candidate?.shutdown() }
        }
        throw EngineException(EngineError.INITIALIZATION, "没有可用的系统 TTS 服务，请先安装语音引擎")
    }
}
