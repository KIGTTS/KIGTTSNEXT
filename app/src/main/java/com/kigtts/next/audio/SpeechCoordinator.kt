package com.kigtts.next.audio

import com.kigtts.engines.recognitionservice.SystemRecognitionPermissionIssue
import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

/** Hardware/session coordination only. Engine policy and implementations remain in plugins. */
class SpeechCoordinator(scope: CoroutineScope, registry: PluginRegistry, audio: AudioPlatform,
    state: () -> AppState, update: ((AppState) -> AppState) -> Unit, message: (String) -> Unit,
    replaceText: (String) -> Unit, selection: (Boolean) -> String?,
    automaticRead: (Boolean) -> Unit = { enabled -> update { it.copy(autoPlayback = enabled) } },
    systemPermissionDenied: (SystemRecognitionPermissionIssue) -> Unit = { message(it.message.orEmpty()) }) {
    private val playback = SpeechPlayback(scope, registry, audio, state, update, message) { selection(true) }
    private val recording = RecognitionTransport(scope, registry, audio, state, update, message, replaceText,
        automaticRead, { text, manual -> if (manual) playback.readOnce(text) else playback.enqueue(text) }, { selection(false) }, systemPermissionDenied)
    val permissionRequests = recording.permissionRequests
    fun permissionResult(granted: Boolean) = recording.permissionResult(granted)
    fun start(session: RecognitionSession) = recording.start(session)
    fun end(submit: Boolean = false, read: Boolean = false) = recording.end(submit, read)
    fun continueInput() = recording.continueInput()
    fun closeListening() = recording.cancel()
    fun toggleListening() = recording.toggleListening()
    fun voiceInput() = recording.voiceInput()
    fun speak(replace: Boolean = false) = playback.speak(replace)
    suspend fun stopSpeaking() = playback.stop()
    suspend fun stopAll() { recording.stop(); playback.stop() }
}
