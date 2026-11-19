package com.kigtts.engines.recognitionservice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.kigtts.plugin.api.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AndroidSystemRecognition(private val context: Context, private val locale: () -> String) : AsrEngine {
    private val requests = Mutex()
    @Volatile private var availabilityProbe: Pair<Long, Boolean>? = null
    override val info get() = AsrInfo(streaming = StreamingKind.NONE, maxBatchSeconds = 15,
        availability = if (isAvailable())
            EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES)

    private fun isAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        availabilityProbe?.takeIf { now - it.first < 5_000 }?.let { return it.second }
        return runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
            .also { availabilityProbe = now to it }
    }

    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult = requests.withLock {
        val capabilities = info
        if (capabilities.availability != EngineAvailability.READY)
            throw EngineException(EngineError.NOT_CONFIGURED, systemRecognitionUnavailable())
        if (Build.VERSION.SDK_INT < 33)
            throw EngineException(EngineError.UNSUPPORTED_FORMAT, "传入录音需要 Android 13 或更新版本")
        if (audio.sampleRate != capabilities.inputFormat.sampleRate || audio.channels != capabilities.inputFormat.channels ||
            audio.bytes.size > audio.sampleRate * audio.channels * 2 * capabilities.maxBatchSeconds)
            throw EngineException(EngineError.UNSUPPORTED_FORMAT, "系统识别需要 16 kHz 单声道录音，每段最多 15 秒")
        if (audio.bytes.isEmpty()) return@withLock RecognitionResult("")
        withContext(Dispatchers.IO) {
            val clip = File.createTempFile("asr-", ".pcm", context.cacheDir)
            try {
                clip.writeBytes(audio.bytes)
                ParcelFileDescriptor.open(clip, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                    // Keep the descriptor, not an accessible file, while the system is transcribing.
                    clip.delete()
                    withContext(Dispatchers.Main.immediate) { decode(source, config) }
                }
            } finally { clip.delete() }
        }
    }

    /** Let Android own the microphone and endpoints for live use. Vendors need not accept external PCM. */
    fun listen(stop: Deferred<Unit>, config: RecognitionConfig, continuousCaptions: Boolean): Flow<RecognitionEvent> = systemCaptionSessions(stop, config) {
        requests.withLock {
            if (stop.isCompleted) return@withLock ""
            if (!isAvailable()) throw EngineException(EngineError.NOT_CONFIGURED, systemRecognitionUnavailable())
            withContext(Dispatchers.Main.immediate) {
                retrySystemRecognitionStartup(stop, { RecognitionCopy.text("system_starting", locale()) }, continuousCaptions) {
                    utterance(intent(config), stop = stop, continuousCaptions = continuousCaptions).text
                }
            }
        }
    }

    private fun intent(config: RecognitionConfig) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    @android.annotation.TargetApi(33)
    private suspend fun decode(source: ParcelFileDescriptor, config: RecognitionConfig): RecognitionResult =
        utterance(intent(config).apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
        }, source = source)

    private suspend fun utterance(request: Intent, source: ParcelFileDescriptor? = null, stop: Deferred<Unit>? = null,
        continuousCaptions: Boolean = false): RecognitionResult = coroutineScope {
        val result = CompletableDeferred<RecognitionResult>()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var ready = false
        val listener = object : RecognitionListener {
            fun finish(text: String) {
                if (result.isCompleted) return
                // Some system vendors ignore EXTRA_AUDIO_SOURCE and open their own microphone.
                // Do not show an unrelated live-microphone result in place of this sentence.
                val consumed = source == null || runCatching { Os.lseek(source.fileDescriptor, 0L, OsConstants.SEEK_CUR) > 0 }.getOrDefault(false)
                if (consumed) result.complete(RecognitionResult(text))
                else result.completeExceptionally(EngineException(EngineError.UNSUPPORTED_FORMAT,
                    "当前系统识别引擎不支持接收断句录音，请切换识别引擎"))
            }
            override fun onResults(results: Bundle?) = finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            override fun onError(error: Int) {
                if (result.isCompleted) return
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    (stop?.isCompleted == true && (!ready || error == SpeechRecognizer.ERROR_CLIENT))) finish("")
                else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && stop != null)
                    result.completeExceptionally(systemRecognitionPermissionIssue(context))
                else if (stop != null && shouldRetrySystemRecognition(error, ready, continuousCaptions)) {
                    if (continuousCaptions) Log.d("SystemCaptionRecognition", "Transient recognizer error $error")
                    result.completeExceptionally(SystemRecognitionStarting())
                } else result.completeExceptionally(systemRecognitionError(error))
            }
            override fun onReadyForSpeech(params: Bundle?) { ready = true }
            override fun onBeginningOfSpeech() { ready = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        var finish: Job? = null
        try {
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(request)
            if (stop != null) finish = launch {
                stop.await()
                if (!result.isCompleted) {
                    if (ready) recognizer.stopListening()
                    else {
                        recognizer.cancel()
                        result.complete(RecognitionResult(""))
                    }
                }
            }
            withTimeoutOrNull(20_000) { result.await() }
                ?: throw EngineException(EngineError.TIMEOUT, "系统识别超时，请重试或切换识别引擎")
        } finally {
            withContext(NonCancellable) {
                finish?.cancelAndJoin()
                result.cancel()
                try { recognizer.cancel() } finally { recognizer.destroy() }
                // Android dispatches the teardown commands through the main looper.
                yield()
            }
        }
    }

    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = flow {
        throw EngineException(EngineError.UNSUPPORTED_FORMAT, "系统识别引擎只接收断句后的录音")
    }
}

private fun systemRecognitionError(error: Int): EngineException = when (error) {
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> EngineException(EngineError.UNAVAILABLE, "系统识别无法联网，请检查网络")
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        EngineException(EngineError.NOT_CONFIGURED, "系统识别不支持当前语言或缺少语言包")
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> EngineException(EngineError.UNAVAILABLE, "系统识别缺少麦克风权限")
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> EngineException(EngineError.UNAVAILABLE, "系统识别正忙，请稍后重试")
    else -> EngineException(EngineError.INFERENCE, "系统识别失败（$error），请重试或切换识别引擎")
}
