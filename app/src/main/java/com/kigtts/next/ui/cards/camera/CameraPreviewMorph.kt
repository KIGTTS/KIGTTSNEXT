package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class CameraPreviewMorph(ratio: Float) {
    val ratio = Animatable(ratio)
    val alpha = Animatable(0f)
    var snapshot by mutableStateOf<Bitmap?>(null)
}

/** One small cached frame per format change. The final live STREAMING frame releases the cover. */
@Composable
internal fun rememberCameraPreviewMorph(view: PreviewView, format: PolaroidFormat, filter: PhotoFilter,
    displayReady: Boolean, morphChanged: (Boolean) -> Unit, strength: Float = 1f): CameraPreviewMorph {
    val motion = remember(view) { CameraPreviewMorph(format.geometry.photoAspectRatio.toFloat()) }
    val ready = rememberUpdatedState(displayReady); val changed = rememberUpdatedState(morphChanged)
    var last by remember(view) { mutableStateOf(format) }
    LaunchedEffect(format, view) {
        if (last == format) return@LaunchedEffect
        last = format
        motion.snapshot = cameraPreviewSnapshot(view) ?: motion.snapshot
        changed.value(true)
        try {
            coroutineScope {
                launch { motion.alpha.animateTo(if (motion.snapshot != null) 1f else 0f, tween(75)) }
                motion.ratio.animateTo(format.geometry.photoAspectRatio.toFloat(), tween(280, easing = FastOutSlowInEasing))
            }
            // Let both Compose measurement and AndroidView layout reach the final rectangle.
            withFrameNanos { }; withFrameNanos { }
        } finally { changed.value(false) }
        snapshotFlow { ready.value }.first { it }
        motion.alpha.animateTo(0f, tween(180))
        motion.snapshot = null
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
