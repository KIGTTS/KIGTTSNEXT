package com.kigtts.next.ui.cards.camera

import androidx.compose.runtime.*
import kotlinx.coroutines.*

/** Files, rather than full-size bitmaps, are queued. Only one exposure and one filter run at a time. */
internal class CameraBurstCapture(private val scope: CoroutineScope,
    private val resolve: (String, String) -> String = { _, fallback -> fallback }) {
    var running by mutableStateOf(false); private set
    var held by mutableStateOf(false); private set
    var count by mutableIntStateOf(0); private set
    var processed by mutableIntStateOf(0); private set
    private var job: Job? = null

    fun start(capture: suspend () -> PolaroidCameraCapture,
        process: suspend (PolaroidCameraCapture) -> PolaroidCameraCapture,
        hasSpace: () -> Boolean, message: (String) -> Unit,
        complete: (List<PolaroidCameraCapture>) -> Unit, finished: () -> Unit): Boolean {
        if (running || !scope.isActive) return false
        held = true; running = true; count = 0; processed = 0
        val task = scope.launch(start = CoroutineStart.LAZY) {
            val photos = mutableListOf<PolaroidCameraCapture>()
            var delivered = false
            try {
                while (held) {
                    ensureActive()
                    if (!hasSpace()) { message(resolve("camera.burst_no_space", "存储空间不足，连拍已停止")); break }
                    try { photos += withTimeout(30_000) { capture() }; count = photos.size }
                    catch (_: TimeoutCancellationException) { message(resolve("camera.burst_timeout", "拍照超时，连拍已停止")); break }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { message(resolve("camera.burst_stopped", "连拍已停止，已拍照片会保留")); break }
                    // Yield even on unusually fast devices; release/stop must always be serviced.
                    if (held) delay(60)
                }
                held = false
                for (index in photos.indices) {
                    ensureActive()
                    photos[index] = process(photos[index]); processed = index + 1
                }
                ensureActive()
                if (photos.isNotEmpty()) { complete(photos.toList()); delivered = true }
            } catch (cancel: CancellationException) {
                // Rotation/backgrounding can end the camera owner. Keep already completed JPEGs for review.
                if (photos.isNotEmpty() && !delivered) {
                    delivered = runCatching { complete(photos.toList()) }.isSuccess
                }
                throw cancel
            } finally {
                if (!delivered) photos.forEach { it.discard() }
                held = false; running = false; job = null; finished()
            }
        }
        job = task
        if (!task.start()) { held = false; running = false; job = null; finished(); return false }
        return true
    }

    /** Let an exposure already in progress finish; do not schedule the next one. */
    fun stop() { held = false }
    fun cancel() { held = false; job?.cancel() }
}
