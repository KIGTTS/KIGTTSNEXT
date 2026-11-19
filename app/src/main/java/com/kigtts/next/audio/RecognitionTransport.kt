package com.kigtts.next.audio

import com.kigtts.engines.recognitionservice.RecognitionServicePlugin
import com.kigtts.engines.recognitionservice.SystemRecognitionPermissionIssue
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
    private val finalizedSpeech: (String, Boolean) -> Unit, private val selection: () -> String?,
    private val copy: (String, String) -> String,
    private val systemPermissionDenied: (SystemRecognitionPermissionIssue) -> Unit = { message(it.message.orEmpty()) },
    private val captionMicrophone: CaptionMicrophone? = null) {
    private var job: Job? = null
    private var generation = 0L
    private var pending: RecognitionSession? = null
    private var stopInput: CompletableDeferred<Unit>? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var submit = false
    private var readOnSubmit = false
    private var currentSession: RecognitionSession? = null
    private var backgrounded = false
    private var captionForegroundAvailable = true
    private var committedText = ""
    private var segmentedSession = false
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val permissionRequests = requests.receiveAsFlow()
    fun permissionResult(granted: Boolean) {
        val session = pending; pending = null
        if (granted) session?.let(::start) else message(copy("listening.mic_denied", "未获得麦克风权限，无法开始录音"))
    }
    private fun service(id: String?): AsrEngine? {
        val engine = registry.plugin(id.orEmpty())?.asr?.takeIf { it.info.role == AsrRole.SERVICE }
            ?: if (id == null) registry.plugins.firstNotNullOfOrNull { it.asr?.takeIf { engine -> engine.info.role == AsrRole.SERVICE } } else null
        if (engine == null) { message(copy("listening.service_disabled", "请先启用语音识别服务")); return null }
        if (engine.info.availability != EngineAvailability.READY) { message(copy("listening.engine_model", "识别引擎尚未就绪，请检查引擎和模型设置")); return null }
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
    fun chatInput(): (suspend (Boolean) -> String?)? {
        val id = selection() ?: return null
        if (service(id) == null) return null
        if (!audio.hasMicrophonePermission()) { requests.trySend(Unit); return null }
        val result = CompletableDeferred<String?>()
        start(RecognitionSession(id, RecognitionPurpose.VOICE_INPUT), result)
        val epoch = generation
        val stop = stopInput
        return { send ->
            try {
                if (send && epoch == generation) {
                    stop?.complete(Unit)
                    withTimeoutOrNull(30_000) { result.await() }?.takeIf { epoch == generation }
                } else null
            } finally {
                if (epoch == generation && (!send || !result.isCompleted)) cancel()
            }
        }
    }

    fun start(session: RecognitionSession) = start(session, null)

    private fun start(session: RecognitionSession, chatResult: CompletableDeferred<String?>?) {
        val engine = service(session.serviceId) ?: return
        val streaming = engine.info.streaming != StreamingKind.NONE
        if (!streaming && (session.purpose == RecognitionPurpose.CAPTIONS || session.continuous)) {
            message(copy("listening.stream_required", "此识别引擎只支持完整录音，请选择支持持续识别的引擎")); return
        }
        if (!audio.hasMicrophonePermission()) { pending = session; requests.trySend(Unit); return }
        pending = null
        val voice = session.purpose == RecognitionPurpose.VOICE_INPUT
        val systemMicrophone = (registry.plugin(session.serviceId) as? RecognitionServicePlugin)?.systemMicrophone(!voice)
        if (session.disableAutomaticRead) automaticRead(false)
        val foreground = if (voice) null else captionMicrophone?.prepare()
        val epoch = ++generation
        val previous = job; previous?.cancel()
        val stop = CompletableDeferred<Unit>(); stopInput = stop; submit = false; readOnSubmit = false
        currentSession = session; committedText = ""
        captionForegroundAvailable = true
        segmentedSession = engine.info.streaming != StreamingKind.INCREMENTAL
        val initialized = CompletableDeferred<Unit>(); ready = initialized
        update { it.copy(listening = true, captionsVisible = !voice && session.output != CaptionOutput.FLOATING,
            transcript = "", recognitionFinalOnly = segmentedSession, voiceInputActive = voice, voiceInputText = "", continuousRecognition = voice && session.continuous,
            voiceInputSubmitting = false, recognitionOutput = session.output) }
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var finalizedSequence = 0L
            fun display(text: String) {
                if (chatResult == null) update { if (voice) it.copy(voiceInputText = text) else it.copy(transcript = text) }
            }
            fun final(text: String) {
                if (voice && state().continuousRecognition && text.isNotBlank()) replaceText(text)
                if (currentSession?.readFinalized == true && state().autoPlayback && text.isNotBlank()) finalizedSpeech(text, false)
            }
            try {
                // A superseded startup must still hand off after the older recorder closes.
                if (previous != null) withContext(NonCancellable) { previous.join() }
                ensureActive()
                if (epoch != generation) return@launch
                if (voice) captionMicrophone?.release()
                if (foreground != null && withTimeoutOrNull(4_500) { foreground.await() } != true) {
                    captionForegroundAvailable = false
                    captionMicrophone?.release()
                    message(copy("listening.background_unavailable", "无法保持后台聆听，请保持 KIGTTS 在前台"))
                    if (backgrounded) return@launch
                }
                if (streaming) (systemMicrophone?.invoke(stop, session.config)
                    ?: engine.recognizeStream(captureUntil(engine.info.inputFormat, stop), session.config)).collect { event ->
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
                    committedText = result.text
                    display(result.text)
                    submit = true // A recorded-only provider completes a bounded recording itself.
                }
                if (chatResult != null && epoch == generation) chatResult.complete(committedText.takeIf(String::isNotBlank))
                if (chatResult == null && voice && !state().continuousRecognition && submit && epoch == generation) {
                    state().voiceInputText.takeIf(String::isNotBlank)?.let { text ->
                        replaceText(text)
                        if (readOnSubmit || state().autoPlayback) finalizedSpeech(text, readOnSubmit)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (issue: SystemRecognitionPermissionIssue) { if (epoch == generation) systemPermissionDenied(issue) }
            catch (error: Exception) {
                if (epoch == generation) {
                    val diagnostic = if (error is EngineException) null else error.message
                    message(diagnostic ?: when ((error as? EngineException)?.code) {
                        EngineError.NOT_CONFIGURED -> copy("listening.engine_setup", "请在语音识别设置中选用可用引擎")
                        EngineError.TIMEOUT -> copy("listening.finish_timeout", "识别结束超时，请重试")
                        else -> copy("listening.failed", "语音识别失败，请重试")
                    })
                }
            }
            finally {
                initialized.complete(Unit)
                if (epoch == generation) {
                    currentSession = null
                    captionMicrophone?.release()
                    update { it.copy(listening = false, voiceInputActive = false, voiceInputSubmitting = false, continuousRecognition = false) }
                }
            }
        }.also { task -> task.invokeOnCompletion { chatResult?.complete(null) } }
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
        if (service(session.serviceId)?.info?.streaming == StreamingKind.NONE) { message(copy("listening.stream_required", "此识别引擎只支持完整录音，请选择支持持续识别的引擎")); return }
        currentSession = session.copy(continuous = true, disableAutomaticRead = true, readFinalized = true)
        automaticRead(false)
        update { it.copy(continuousRecognition = true) }
        committedText.takeIf(String::isNotBlank)?.let(replaceText)
    }
    fun end(send: Boolean, read: Boolean = false) {
        if (!state().voiceInputActive || state().continuousRecognition || !send) { cancel(); return }
        if (submit) return
        update { it.copy(voiceInputSubmitting = true) }
        submit = true; readOnSubmit = read; stopInput?.complete(Unit)
        val finishing = job; val initialized = ready; val epoch = generation
        val finishBudget = if (segmentedSession) 25_000L else 3_000L
        scope.launch {
            val finished = withTimeoutOrNull(30_000) {
                initialized?.await()
                withTimeoutOrNull(finishBudget) { finishing?.join(); true } == true
            } == true
            if (!finished && epoch == generation) { cancel(); message(copy("listening.finish_timeout", "识别结束超时，请重试")) }
        }
    }
    fun onForeground() { backgrounded = false }
    fun onBackground(hasCaptionWindow: Boolean): Boolean {
        backgrounded = true
        if (hasCaptionWindow && currentSession?.purpose == RecognitionPurpose.CAPTIONS && captionForegroundAvailable) return true
        cancel()
        return false
    }
    fun cancelCaptions() {
        if (currentSession?.purpose == RecognitionPurpose.CAPTIONS || pending?.purpose == RecognitionPurpose.CAPTIONS) cancel()
        else if (state().captionsVisible) update { it.copy(captionsVisible = false) }
    }
    fun cancel() { scope.launch(start = CoroutineStart.UNDISPATCHED) { stop() } }

    suspend fun stop() {
        val epoch = ++generation
        val closing = job
        pending = null
        stopInput = null
        currentSession = null
        committedText = ""
        closing?.cancel()
        update { it.copy(captionsVisible = false, listening = false, voiceInputActive = false,
            voiceInputSubmitting = false, continuousRecognition = false, voiceInputText = "") }
        // Keep the microphone service promoted until the recognizer and recorder have unwound.
        withContext(NonCancellable) {
            closing?.join()
            if (epoch == generation) {
                job = null
                captionMicrophone?.release()
            }
        }
    }
}
