package com.kigtts.next.ui.cards.camera

import android.os.SystemClock
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.util.UUID

internal class CameraMotionTicket internal constructor(internal var shutterAtMs: Long) {
    internal val finished = CompletableDeferred<List<CameraMotionPart>>()
}

/** Bounded, real pre-recording. The shutter retains recent encoded segments and a short post exposure tail. */
internal class CameraMotionBuffer(private val directory: File, private val source: CameraMotionSource,
    private val scope: CoroutineScope, private val message: (String) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val resolve: (String, String) -> String = { _, fallback -> fallback },
    private val mux: (List<CameraMotionPart>, Long, File) -> CameraMotionVideo = CameraMotionMuxer::write) : Closeable {
    private class Active(val file: File, var startedAtMs: Long) {
        var recording: CameraMotionRecording? = null
        var started = false
    }
    var ready by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    private var current: Active? = null
    private val parts = mutableListOf<CameraMotionPart>()
    private var ticket: CameraMotionTicket? = null
    private var stopJob: Job? = null
    private var closed = false
    private var stopping = false

    fun start() {
        if (closed || failed || current != null || ticket != null || !scope.isActive) return
        try {
            check(directory.mkdirs() || directory.isDirectory)
            val active = Active(File(directory, "${UUID.randomUUID()}-buffer.mp4"), now())
            current = active
            active.recording = source.start(active.file) { event -> accept(active, event) }
        } catch (_: Exception) { failPreparation() }
    }
    private fun accept(active: Active, event: CameraMotionEvent) {
        if (closed || current !== active) {
            if (event is CameraMotionEvent.Finished) active.file.delete()
            return
        }
        when (event) {
            is CameraMotionEvent.Started -> { active.started = true; active.startedAtMs = now() - event.durationMs }
            is CameraMotionEvent.Progress -> if (event.durationMs >= 250) ready = true
            is CameraMotionEvent.Finished -> {
                current = null
                if (!event.usable || event.durationMs <= 0 || !active.file.isFile) {
                    active.file.delete(); failPreparation(); return
                }
                parts += CameraMotionPart(active.file, active.startedAtMs, event.durationMs)
                if (ticket == null) while (parts.size > 2) parts.removeAt(0).file.delete()
                val capture = ticket
                if (capture != null && stopping) capture.finished.complete(parts.toList())
                else if (!closed && !failed) {
                    // Keep the existing capture ticket while crossing a segment boundary.
                    val pending = ticket; ticket = null; start(); ticket = pending
                }
            }
        }
    }
    fun beginCapture(): CameraMotionTicket? {
        if (!ready || closed || failed || current?.started != true || ticket != null) return null
        return CameraMotionTicket(now()).also { ticket = it; stopping = false; armStop(it) }
    }
    fun exposureStarted(capture: CameraMotionTicket) {
        if (ticket !== capture || stopping || closed) return
        capture.shutterAtMs = now(); armStop(capture)
    }
    private fun armStop(capture: CameraMotionTicket) {
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(POST_ROLL_MS)
            if (ticket === capture && !closed) {
                stopping = true
                val active = current
                if (active != null) active.recording?.stop() else capture.finished.complete(parts.toList())
            }
        }
    }
    suspend fun finish(capture: CameraMotionTicket, colorMatrix: FloatArray,
        lut: com.kigtts.next.media.color.CubeLut? = null, strength: Float = 1f,
        effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE): CameraMotionClip? {
        val destination = File(directory, "${UUID.randomUUID()}-clip.mp4")
        return try {
            val saved = withTimeout(6_000) { capture.finished.await() }
            val relevant = saved.filter { it.startedAtMs + it.durationMs > capture.shutterAtMs - PRE_ROLL_MS &&
                it.startedAtMs <= capture.shutterAtMs + POST_ROLL_MS }.sortedBy { it.startedAtMs }
            require(relevant.isNotEmpty())
            val result = withContext(Dispatchers.IO) { mux(relevant, capture.shutterAtMs, destination) }
            require(result.durationMs > 0 && destination.isFile && destination.length() > 0)
            CameraMotionClip(destination, result.durationMs, result.rotationDegrees, result.shutterPositionMs, colorMatrix.copyOf(), lut, strength, effects)
        } catch (_: TimeoutCancellationException) {
            destination.delete(); currentCoroutineContext().ensureActive()
            message(resolve("camera.motion_save_warning", "实况短片未能保存，本次保留照片")); null
        }
        catch (cancel: CancellationException) { destination.delete(); throw cancel }
        catch (_: Exception) { destination.delete(); message(resolve("camera.motion_save_warning", "实况短片未能保存，本次保留照片")); null }
        finally {
            if (ticket === capture) {
                ticket = null; stopJob?.cancel(); stopJob = null; ready = false
                parts.forEach { it.file.delete() }; parts.clear()
            }
        }
    }
    fun abort(capture: CameraMotionTicket?) {
        if (capture == null || ticket !== capture) return
        ticket = null; capture.finished.cancel(); stopJob?.cancel(); stopJob = null
        stopping = false; ready = false
        current?.recording?.stop()
        parts.forEach { it.file.delete() }; parts.clear()
    }
    private fun failPreparation() {
        if (failed || closed) return
        failed = true; ready = false
        current?.recording?.stop(); current?.file?.delete(); current = null
        parts.forEach { it.file.delete() }; parts.clear()
        ticket?.finished?.complete(emptyList())
        message(resolve("camera.motion_device_unsupported", "此相机暂不支持实况录制，仍可正常拍照"))
    }
    override fun close() {
        if (closed) return
        closed = true; ready = false; stopJob?.cancel(); stopJob = null
        ticket?.finished?.cancel(); ticket = null
        val active = current; current = null
        active?.recording?.stop(); active?.file?.delete()
        parts.forEach { it.file.delete() }; parts.clear()
    }
    companion object {
        const val PRE_ROLL_MS = 2_000L
        const val POST_ROLL_MS = 1_200L
        const val SEGMENT_MS = 6_000L
    }
}
