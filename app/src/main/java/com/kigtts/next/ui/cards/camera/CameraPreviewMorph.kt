package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val CameraFramingMotion = tween<Float>(320, easing = FastOutSlowInEasing)

internal class CameraPreviewMorph(ratio: Float, fullFrame: Boolean) {
    val ratio = Animatable(ratio)
    val fullFrameProgress = Animatable(if (fullFrame) 1f else 0f)
}

/** Move the measured preview before the camera session binds to its new crop. */
@Composable
internal fun rememberCameraPreviewMorph(view: PreviewView, aspectRatio: Float, fullFrame: Boolean,
    morphChanged: (Boolean) -> Unit): CameraPreviewMorph {
    val motion = remember(view) { CameraPreviewMorph(aspectRatio, fullFrame) }
    val changed = rememberUpdatedState(morphChanged)
    val stageSettled = rememberUpdatedState(LocalCameraStageSettled.current)
    val framing = aspectRatio to fullFrame
    var last by remember(view) { mutableStateOf(framing) }
    LaunchedEffect(framing, view) {
        if (last == framing) return@LaunchedEffect
        last = framing
        changed.value(true)
        try {
            coroutineScope {
                launch { motion.ratio.animateTo(aspectRatio, CameraFramingMotion) }
                launch { motion.fullFrameProgress.animateTo(if (fullFrame) 1f else 0f, CameraFramingMotion) }
            }
            snapshotFlow { stageSettled.value }.first { it }
            // Let both Compose measurement and AndroidView layout reach the final rectangle.
            withFrameNanos { }; withFrameNanos { }
        } finally { changed.value(false) }
    }
    return motion
}

internal fun cameraPreviewSnapshot(view: PreviewView): Bitmap? {
    val original = try { view.bitmap } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null } ?: return null
    var small: Bitmap? = null
    var retained: Bitmap? = null
    try {
        val scale = minOf(1f, 384f / maxOf(original.width, original.height).coerceAtLeast(1))
        small = Bitmap.createScaledBitmap(original, (original.width * scale).roundToInt().coerceAtLeast(1),
            (original.height * scale).roundToInt().coerceAtLeast(1), true)
        // The CameraX preview buffer already contains the filter. Keep that frame unchanged.
        retained = small
        return small
    } catch (_: Exception) { return null } catch (_: OutOfMemoryError) { return null }
    finally {
        if (small !== retained) small?.recycle()
        if (original !== retained && original !== small) original.recycle()
    }
}
