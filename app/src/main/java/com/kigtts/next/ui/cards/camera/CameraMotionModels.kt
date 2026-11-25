package com.kigtts.next.ui.cards.camera

import java.io.File

/** Raw encoded video. Preview/export apply the same matrix as the separately preserved still image. */
class CameraMotionClip(val video: File, val durationMs: Long, val rotationDegrees: Int,
    val shutterPositionMs: Long, val colorMatrix: FloatArray,
    val lut: com.kigtts.next.media.color.CubeLut? = null, val lutStrength: Float = 1f,
    val effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE)

/** A finalized recording segment; its origin uses Android's monotonic elapsed realtime clock. */
internal data class CameraMotionPart(val file: File, val startedAtMs: Long, val durationMs: Long)
internal data class CameraMotionVideo(val durationMs: Long, val rotationDegrees: Int, val shutterPositionMs: Long)

internal sealed interface CameraMotionEvent {
    data class Started(val durationMs: Long) : CameraMotionEvent
    data class Progress(val durationMs: Long) : CameraMotionEvent
    data class Finished(val durationMs: Long, val usable: Boolean, val retryable: Boolean = false) : CameraMotionEvent
}

internal fun interface CameraMotionRecording { fun stop() }
internal fun interface CameraMotionSource {
    fun start(file: File, events: (CameraMotionEvent) -> Unit): CameraMotionRecording
}
