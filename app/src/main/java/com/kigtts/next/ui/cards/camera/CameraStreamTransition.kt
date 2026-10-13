package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.annotation.MainThread
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A single small, already-filtered camera frame covers the gap between live streams. */
@MainThread
internal class CameraStreamTransition(view: PreviewView,
    private val capture: () -> Bitmap? = { cameraPreviewSnapshot(view) }) : AutoCloseable {
    var snapshot by mutableStateOf<Bitmap?>(null); private set
    val alpha = Animatable(0f)
    private var generation = 0L
    private var closed = false
    private var animation: Job? = null

    /** Called before unbinding the old camera. Capturing never runs in the animation loop. */
    suspend fun cover() {
        if (closed) return
        val request = ++generation
        snapshot = capture() ?: snapshot
        if (snapshot == null) return
        animate(request, 1f, 75)
    }

    /** Called only after the replacement PreviewView reports its actual streamed frame. */
    suspend fun reveal() {
        if (closed || snapshot == null) return
        val request = generation
        withFrameNanos { }; withFrameNanos { }
        if (closed || request != generation) return
        animate(request, 0f, 180)
        if (!closed && request == generation && alpha.value <= .00001f) snapshot = null
    }

    private suspend fun animate(request: Long, target: Float, duration: Int) = coroutineScope {
        if (closed || request != generation) return@coroutineScope
        animation?.cancel()
        val current = launch(start = CoroutineStart.UNDISPATCHED) {
            if (!closed && request == generation) alpha.animateTo(target, tween(duration))
        }
        animation = current
        try { current.join() } finally { if (animation === current) animation = null }
    }

    override fun close() {
        if (closed) return
        closed = true; generation++
        animation?.cancel(); animation = null
        // Published bitmaps may still be used by Compose/RenderThread or the pre-31 blur
        // worker. Drop our reference; never recycle a bitmap underneath those consumers.
        snapshot = null
    }
}
