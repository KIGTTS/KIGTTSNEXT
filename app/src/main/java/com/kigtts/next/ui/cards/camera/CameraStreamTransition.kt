package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.LocalActivity
import androidx.annotation.MainThread
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kigtts.next.ui.media.BlurredBackdrop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class CameraTransitionFrame(val bitmap: Bitmap, val rotation: Int)

/** A single small, already-filtered camera frame covers the gap between live streams. */
@MainThread
internal class CameraStreamTransition(view: PreviewView,
    retained: CameraTransitionFrame? = null,
    private val capture: () -> Bitmap? = {
        if (view.previewStreamState.value == PreviewView.StreamState.STREAMING) cameraPreviewSnapshot(view) else null
    }) : AutoCloseable {
    var frame by mutableStateOf(retained); private set
    var lastGoodFrame = retained; private set
    val alpha = Animatable(if (retained != null) 1f else 0f)
    private var generation = 0L
    private var closed = false
    private var animation: Job? = null
    private var revealing = false

    fun cacheLiveFrame(rotation: Int): Boolean {
        if (closed) return false
        val image = capture() ?: return false
        // A TextureView can return an allocated but blank bitmap while its surface is replaced.
        if (!image.hasPreviewPixels()) {
            image.recycle()
            return false
        }
        lastGoodFrame = CameraTransitionFrame(image, rotation)
        return true
    }

    /** Called before unbinding the old camera. Capturing never runs in the animation loop. */
    suspend fun cover(liveRotation: Int?) {
        if (closed) return
        val request = ++generation
        val live = liveRotation != null && (frame != null && !revealing || cacheLiveFrame(liveRotation))
        frame = lastGoodFrame
        revealing = false
        if (frame == null) return
        // With no usable live surface below us, a partial alpha would expose black.
        animate(request, 1f, if (live) 75 else 0)
    }

    /** Called only after the replacement PreviewView reports its actual streamed frame. */
    suspend fun reveal(rotation: Int) {
        if (closed) return
        val request = generation
        withFrameNanos { }; withFrameNanos { }
        if (closed || request != generation) return
        cacheLiveFrame(rotation)
        if (frame == null) return
        revealing = true
        animate(request, 0f, 180)
        if (!closed && request == generation && alpha.value <= .00001f) {
            frame = null
            revealing = false
        }
    }

    private suspend fun animate(request: Long, target: Float, duration: Int) = coroutineScope {
        if (closed || request != generation) return@coroutineScope
        animation?.cancel()
        if (alpha.value == target) return@coroutineScope
        val current = launch(start = CoroutineStart.UNDISPATCHED) {
            if (!closed && request == generation) {
                if (duration == 0) alpha.snapTo(target) else alpha.animateTo(target, tween(duration))
            }
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
        frame = null
        lastGoodFrame = null
    }
}

private fun Bitmap.hasPreviewPixels(): Boolean {
    for (row in 0..8) for (column in 0..8) {
        val pixel = getPixel(column * (width - 1) / 8, row * (height - 1) / 8)
        if ((pixel ushr 24) != 0 && (pixel and 0x00FF_FFFF) != 0) return true
    }
    return false
}

/** A single frame can survive an Activity recreation, scoped to the same camera visit. */
internal class CameraRotationFrames : ViewModel() {
    private var pending: Pair<String, CameraTransitionFrame>? = null

    fun retain(visit: String, frame: CameraTransitionFrame?) { pending = frame?.let { visit to it } }

    fun take(visit: String): CameraTransitionFrame? =
        pending?.takeIf { it.first == visit }?.second.also { pending = null }
}

@Composable
internal fun rememberCameraStreamTransition(view: PreviewView, visit: String, liveRotation: Int?): CameraStreamTransition {
    val retained: CameraRotationFrames = viewModel()
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRotation by rememberUpdatedState(liveRotation)
    val transition = remember(view, visit) { CameraStreamTransition(view, retained.take(visit)) }
    DisposableEffect(transition, lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentRotation?.let(transition::cacheLiveFrame)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations == true) retained.retain(visit, transition.lastGoodFrame)
            transition.close()
        }
    }
    return transition
}

@Composable
internal fun CameraTransitionCover(transition: CameraStreamTransition, rotation: Int, modifier: Modifier) {
    val frame = transition.frame ?: return
    val source = remember(frame, rotation) {
        if (rotation == frame.rotation) frame.bitmap
        else {
            // CameraX's relative rotation subtracts the new display rotation from the sensor rotation.
            val degrees = (frame.rotation - rotation) * 90f
            Bitmap.createBitmap(frame.bitmap, 0, 0, frame.bitmap.width, frame.bitmap.height,
                Matrix().apply { setRotate(degrees) }, true)
        }
    }
    BlurredBackdrop(source, modifier.graphicsLayer { alpha = transition.alpha.value }
        .testTag("camera-stream-cover"), showSourceWhileLoading = true)
}
