package com.kigtts.next.audio

import android.media.audiofx.NoiseSuppressor

internal fun systemRecognitionNoiseAvailable(): Boolean =
    runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)

/** Some audio sources attach this effect implicitly. Explicitly apply the preference per recording. */
internal fun recognitionNoise(sessionId: Int, enabled: Boolean): NoiseSuppressor? {
    if (!systemRecognitionNoiseAvailable()) return null
    val effect = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull() ?: return null
    runCatching { effect.setEnabled(enabled) }
    return effect
}
