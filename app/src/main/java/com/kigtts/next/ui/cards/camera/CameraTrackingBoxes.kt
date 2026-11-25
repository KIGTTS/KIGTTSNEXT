package com.kigtts.next.ui.cards.camera

import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.CameraFaceFrame
import com.kigtts.next.camera.expressions.FACE_HOLD_MS
import com.kigtts.next.camera.expressions.FaceConfirmation
import com.kigtts.next.camera.expressions.faceCanTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/** Focus boxes are the viewfinder's chrome. Only the canvas is redrawn during tracking. */
@Composable internal fun CameraTrackingBoxes(view: PreviewView, session: PolaroidCameraSession, modifier: Modifier) {
    var boxes by remember(view, session) { mutableStateOf(emptyList<TrackingBox>()) }
    LaunchedEffect(view, session) {
        val motion = TrackingBoxMotion()
        var animation: Job? = null
        var expiry: Job? = null
        var lastFrameAt = 0L
        fun animate() {
            if (animation?.isActive == true) return
            animation = launch {
                do {
                    val more = withFrameNanos {
                        boxes = motion.render(SystemClock.elapsedRealtime())
                        motion.isAnimating
                    }
                } while (more)
            }
        }
        snapshotFlow { session.trackedFaces }.collect { frame ->
            val now = SystemClock.elapsedRealtime()
            lastFrameAt = now
            motion.observe(frame?.takeIf { it.isCurrent(now) }.boxesIn(view), frame?.arrivedAt ?: now)
            if (motion.hasBoxes) animate()
            if (frame != null && expiry?.isActive != true) expiry = launch {
                do {
                    val remaining = lastFrameAt + 400L - SystemClock.elapsedRealtime()
                    if (remaining > 0) delay(remaining)
                } while (lastFrameAt + 400L > SystemClock.elapsedRealtime())
                motion.observe(emptyMap(), SystemClock.elapsedRealtime())
                if (motion.hasBoxes) animate()
            }
        }
    }
    Canvas(modifier) {
        for (box in boxes) {
            val rect = box.rect
            val origin = Offset(rect.left * size.width, rect.top * size.height)
            val extent = Size(rect.width * size.width, rect.height * size.height)
            val radius = CornerRadius(5.dp.toPx())
            val alpha = .84f * box.alpha
            drawRoundRect(Color.Black.copy(alpha = alpha * .55f), origin, extent, radius, style = Stroke(2.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = alpha), origin, extent, radius, style = Stroke(1.dp.toPx()))
        }
    }
}

private fun CameraFaceFrame?.boxesIn(view: PreviewView): Map<Long, Rect> {
    val frame = this ?: return emptyMap()
    val transform = frame.toView(view) ?: return emptyMap()
    val width = view.width
    val height = view.height
    return buildMap {
        for (face in frame.faces) {
            val bounds = RectF(face.bounds).also { transform.mapRect(it) }
            if (!faceCanTrack(face, bounds, width, height)) continue
            put(face.id, Rect(bounds.left / width, bounds.top / height, bounds.right / width, bounds.bottom / height))
        }
    }
}

private data class TrackingBox(val rect: Rect, val alpha: Float)

private class TrackingBoxMotion {
    private class Track(var shown: Rect, var target: Rect, var alpha: Float, var lastSeen: Long) {
        var present = true
    }
    private val confirmations = FaceConfirmation()
    private val tracks = mutableMapOf<Long, Track>()
    private var tick = 0L
    var isAnimating = false; private set
    val hasBoxes get() = tracks.isNotEmpty()

    fun observe(boxes: Map<Long, Rect>, at: Long) {
        val confirmed = confirmations.update(boxes.keys.toList(), at)
        tracks.values.forEach { it.present = false }
        boxes.forEach { (id, rect) ->
            val track = tracks[id]
            if (track != null) {
                track.target = rect
                track.lastSeen = at
                track.present = true
            } else if (id in confirmed) {
                tracks[id] = Track(rect, rect, 0f, at)
            }
        }
    }

    fun render(now: Long): List<TrackingBox> {
        val dt = if (tick == 0L) 16L else (now - tick).coerceIn(1L, 32L)
        tick = now
        val follow = 1f - exp(-dt.toFloat() / 58f)
        isAnimating = false
        return buildList {
            val entries = tracks.values.iterator()
            while (entries.hasNext()) {
                val track = entries.next()
                val elapsed = now - track.lastSeen
                val fading = !track.present && elapsed > FACE_HOLD_MS
                track.alpha = if (fading) (track.alpha - dt / 140f).coerceAtLeast(0f)
                    else (track.alpha + dt / 145f).coerceAtMost(1f)
                if (fading && track.alpha <= 0f) {
                    entries.remove()
                    continue
                }
                val moved = track.shown.edgeDistance(track.target) > .0007f
                track.shown = if (moved) lerp(track.shown, track.target, follow) else track.target
                // While waiting for the short hold to finish, equal boxes do not invalidate drawing.
                if (moved || track.alpha < 1f || !track.present) isAnimating = true
                if (track.alpha > 0f) add(TrackingBox(track.shown, track.alpha))
            }
        }
    }
}

private fun Rect.edgeDistance(other: Rect): Float =
    maxOf(abs(left - other.left), abs(top - other.top), abs(right - other.right), abs(bottom - other.bottom))
