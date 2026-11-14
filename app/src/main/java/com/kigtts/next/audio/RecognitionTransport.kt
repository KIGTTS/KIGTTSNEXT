package com.kigtts.next.audio

import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

/** One microphone lease. Submit closes PCM with EOF; Cancel does not submit an unfinished hypothesis. */
internal class RecognitionTransport(private val scope: CoroutineScope, private val registry: PluginRegistry,
    private val audio: AudioPlatform, private val state: () -> AppState,
    private val update: ((AppState) -> AppState) -> Unit, private val message: (String) -> Unit,
    private val replaceText: (String) -> Unit, private val automaticRead: (Boolean) -> Unit,
    private val finalizedSpeech: (String, Boolean) -> Unit, private val selection: () -> String?) {
    private var job: Job? = null
    private var generation = 0L
    private var pending: RecognitionSession? = null
    private var stopInput: CompletableDeferred<Unit>? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var submit = false
    private var readOnSubmit = false
    private var currentSession: RecognitionSession? = null
    private var committedText = ""
    private var segmentedSession = false
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val permissionRequests = requests.receiveAsFlow()
    fun permissionResult(granted: Boolean) {
        val session = pending; pending = null
        if (granted) session?.let(::start) else message("未获得麦克风权限，无法开始录音")
    }
    private fun service(id: String?): AsrEngine? {
        val engine = registry.plugin(id.orEmpty())?.asr?.takeIf { it.info.role == AsrRole.SERVICE }
            ?: if (id == null) registry.plugins.firstNotNullOfOrNull { it.asr?.takeIf { engine -> engine.info.role == AsrRole.SERVICE } } else null
        if (engine == null) { message("请先安装并启用语音识别服务插件"); return null }
        if (engine.info.availability != EngineAvailability.READY) { message("识别服务尚未选用就绪引擎，请配置对应模型"); return null }
        return engine
    }
    fun toggleListening() {
        if (state().captionsVisible || state().listening && !state().voiceInputActive) { cancel(); return }
        val id = selection() ?: registry.plugins.firstOrNull { it.asr?.info?.role == AsrRole.SERVICE }?.descriptor?.id
        if (service(id) == null) return
        start(RecognitionSession(requireNotNull(id), RecognitionPurpose.CAPTIONS))
    }
    fun voiceInput() {
        val id = selection() ?: registry.plugins.firstOrNull { it.asr?.info?.role == AsrRole.SERVICE }?.descriptor?.id
        if (service(id) == null) return
        start(RecognitionSession(requireNotNull(id), RecognitionPurpose.VOICE_INPUT))
    }
    fun start(session: RecognitionSession) {
        val engine = service(session.serviceId) ?: return
        val streaming = engine.info.streaming != StreamingKind.NONE
        if (!streaming && (session.purpose == RecognitionPurpose.CAPTIONS || session.continuous)) {
            message("此识别包只能识别完整录音，请选用流式识别包"); return
        }
        if (!audio.hasMicrophonePermission()) { pending = session; requests.trySend(Unit); return }
        pending = null
        if (session.disableAutomaticRead) automaticRead(false)
        val previous = job; previous?.cancel()
        val epoch = ++generation
        val stop = CompletableDeferred<Unit>(); stopInput = stop; submit = false; readOnSubmit = false
        currentSession = session; committedText = ""
        segmentedSession = engine.info.streaming != StreamingKind.INCREMENTAL
        val initialized = CompletableDeferred<Unit>(); ready = initialized
        job = scope.launch {
            previous?.join()
            if (epoch != generation) return@launch
            val voice = session.purpose == RecognitionPurpose.VOICE_INPUT
            update { it.copy(listening = true, captionsVisible = !voice && session.output != CaptionOutput.FLOATING,
                transcript = "", voiceInputActive = voice, voiceInputText = "", continuousRecognition = voice && session.continuous,
                recognitionOutput = session.output) }
            var finalizedSequence = 0L
            fun display(text: String) { update { if (voice) it.copy(voiceInputText = text) else it.copy(transcript = text) } }
            fun final(text: String) {
                if (voice && state().continuousRecognition && text.isNotBlank()) replaceText(text)
                if (currentSession?.readFinalized == true && state().autoPlayback && text.isNotBlank()) finalizedSpeech(text, false)
            }
            try {
                if (streaming) engine.recognizeStream(captureUntil(engine.info.inputFormat, stop), session.config).collect { event ->
                    if (epoch != generation) return@collect
                    when (event) {
                        RecognitionEvent.Started -> initialized.complete(Unit)
                        is RecognitionEvent.Partial -> { initialized.complete(Unit); display(event.text) }
                        is RecognitionEvent.Final -> { initialized.complete(Unit); committedText = event.result.text; display(event.result.text); final(event.result.text) }
                        is RecognitionEvent.Transcript -> {
                            initialized.complete(Unit)
                            committedText = event.committed
                            display(listOf(event.committed, event.partial).filter(String::isNotBlank).joinToString("\n"))
                            if (event.sequence > finalizedSequence) {
                                finalizedSequence = event.sequence
                                event.finalized?.let(::final)
                            }
                        }
                    }
                } else {
                    val result = engine.recognize(recording(engine.info.inputFormat, stop, minOf(5, engine.info.maxBatchSeconds)), session.config)
                    display(result.text)
                    submit = true // A recorded-only provider completes a bounded recording itself.
                }
                if (voice && !state().continuousRecognition && submit && epoch == generation) {
                    state().voiceInputText.takeIf(String::isNotBlank)?.let { text ->
                        replaceText(text)
                        if (readOnSubmit || state().autoPlayback) finalizedSpeech(text, readOnSubmit)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (epoch == generation) message(error.message ?: "语音识别失败") }
            finally {
                initialized.complete(Unit)
                if (epoch == generation) update { it.copy(listening = false, voiceInputActive = false, continuousRecognition = false) }
            }
        }
    }
    private fun captureUntil(format: AudioFormat, stop: Deferred<Unit>): Flow<PcmAudio> = channelFlow {
        val capture = launch { audio.capture(format).collect { send(it) } }
        val finish = launch { stop.await(); capture.cancel() }
        try { capture.join() } finally { finish.cancelAndJoin(); capture.cancelAndJoin() }
    }
    private suspend fun recording(format: AudioFormat, stop: Deferred<Unit>, seconds: Int): PcmAudio {
        val bytes = ByteArrayOutputStream()
        val maximum = format.sampleRate * format.channels * 2 * seconds
        captureUntil(format, stop).takeWhile { bytes.size() < maximum }.collect { chunk ->
            require(chunk.sampleRate == format.sampleRate && chunk.channels == format.channels)
            bytes.write(chunk.bytes, 0, minOf(chunk.bytes.size, maximum - bytes.size()))
        }
        return PcmAudio(bytes.toByteArray(), format.sampleRate, format.channels)
    }
    /** Switching the overlay to continuous input keeps the current microphone and engine session. */
    fun continueInput() {
        val session = currentSession ?: return
        if (!state().voiceInputActive || state().continuousRecognition) return
        if (service(session.serviceId)?.info?.streaming == StreamingKind.NONE) { message("持续识别需要流式识别包"); return }
        currentSession = session.copy(continuous = true, disableAutomaticRead = true, readFinalized = true)
        automaticRead(false)
        update { it.copy(continuousRecognition = true) }
        committedText.takeIf(String::isNotBlank)?.let(replaceText)
    }
    fun end(send: Boolean, read: Boolean = false) {
        if (!state().voiceInputActive || state().continuousRecognition || !send) { cancel(); return }
        submit = true; readOnSubmit = read; stopInput?.complete(Unit)
        val finishing = job; val initialized = ready; val epoch = generation
        val finishBudget = if (segmentedSession) 25_000L else 3_000L
        scope.launch {
            val finished = withTimeoutOrNull(30_000) {
                initialized?.await()
                withTimeoutOrNull(finishBudget) { finishing?.join(); true } == true
            } == true
            if (!finished && epoch == generation) { cancel(); message("识别结束超时，请重试") }
        }
    }
    fun cancel() {
        pending = null; generation++; stopInput = null; currentSession = null; committedText = ""; job?.cancel()
        update { it.copy(captionsVisible = false, listening = false, voiceInputActive = false, continuousRecognition = false, voiceInputText = "") }
    }
    suspend fun stop() { val previous = job; cancel(); previous?.join() }
}
