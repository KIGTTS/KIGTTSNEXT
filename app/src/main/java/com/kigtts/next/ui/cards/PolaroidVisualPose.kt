package com.kigtts.next.ui.cards

import androidx.compose.runtime.compositionLocalOf

internal data class PolaroidVisualPose(val rotation: Float = 0f, val opacity: Float = 1f) {
    fun toward(target: PolaroidVisualPose, progress: Float) = PolaroidVisualPose(
        rotation + (target.rotation - rotation) * progress,
        opacity + (target.opacity - opacity) * progress)
}

internal val LocalPolaroidVisualPose = compositionLocalOf<() -> PolaroidVisualPose> { { PolaroidVisualPose() } }
internal val LocalPolaroidEntering = compositionLocalOf { false }
