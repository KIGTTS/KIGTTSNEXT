package com.kigtts.next.ui.cards.camera

import androidx.camera.core.CameraState
import androidx.camera.core.SessionConfig
import androidx.camera.view.PreviewView
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Own the exact modern session, including the period before an asynchronously started camera is handed off. */
internal class CameraSessionLease(val session: SessionConfig, private val unbind: (SessionConfig) -> Unit) {
    private var closed = false
    fun close() { if (!closed) { closed = true; unbind(session) } }
}

internal class CameraStartupException(message: String) : IllegalStateException(message)

/** A synchronous bind return means allocated, not opened. Every failed/cancelled startup relinquishes ownership. */
internal suspend fun <T> finishCameraStartup(binding: T, release: (T) -> Unit, start: suspend () -> Unit): T {
    var transferred = false
    try {
        start(); currentCoroutineContext().ensureActive()
        transferred = true
        return binding
    } finally { if (!transferred) release(binding) }
}

internal suspend fun awaitCameraReady(state: LiveData<CameraState>, stream: LiveData<PreviewView.StreamState>,
    timeoutMillis: Long = 8_000) {
    val started = withTimeoutOrNull(timeoutMillis) {
        combine(state.cameraSignals(), stream.cameraSignals()) { camera, preview ->
            camera.error?.let { throw CameraStartupException("Camera startup error ${it.code}: ${camera.type}") }
            camera.type == CameraState.Type.OPEN && preview == PreviewView.StreamState.STREAMING
        }.first { it }
        true
    } ?: false
    if (!started) throw CameraStartupException("Camera startup timeout: state=${state.value?.type}, preview=${stream.value}")
}

internal suspend fun awaitCameraClosed(state: LiveData<CameraState>) {
    state.cameraSignals().first { it.type == CameraState.Type.CLOSED }
}

internal suspend fun awaitCameraPreviewIdle(stream: LiveData<PreviewView.StreamState>) {
    val idle = withTimeoutOrNull(2_500) {
        stream.cameraSignals().first { it == PreviewView.StreamState.IDLE }
        true
    } ?: false
    if (!idle) throw CameraStartupException("Previous preview has not stopped")
}

/** Registration, cancellation and cleanup all use the main dispatcher, including cancellation from an IO caller. */
private fun <T : Any> LiveData<T>.cameraSignals(): Flow<T> = callbackFlow {
    val observer = Observer<T> { value -> value?.let { trySend(it) } }
    var registered = false
    try {
        withContext(Dispatchers.Main.immediate) { registered = true; observeForever(observer) }
        awaitClose()
    } finally {
        withContext(NonCancellable + Dispatchers.Main.immediate) { if (registered) removeObserver(observer) }
    }
}.conflate()
