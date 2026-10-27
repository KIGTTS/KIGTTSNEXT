package com.kigtts.next.audio

import kotlin.math.abs
import kotlin.math.roundToInt

internal const val SPEECH_GAIN_ACTION = "host-audio/gain/value"

/** Give 0–1 and 1–10 equal track space, with a small neutral detent. */
internal fun speechGainPosition(gain: Float): Float = if (gain <= 1f) gain.coerceIn(0f, 1f) / 2f else .5f + (gain.coerceAtMost(10f) - 1f) / 18f
internal fun speechGainAt(position: Float): Float {
    val p = position.coerceIn(0f, 1f)
    if (abs(p - .5f) <= .018f) return 1f
    return if (p < .5f) p * 2f else 1f + (p - .5f) * 18f
}

/** Apply gain to signed little-endian PCM16 without modifying a provider's buffer. */
internal fun speechPcmGain(bytes: ByteArray, gain: Float): ByteArray {
    val safe = gain.takeIf(Float::isFinite)?.coerceIn(0f, 10f) ?: 1f
    if (safe == 1f) return bytes
    require(bytes.size % 2 == 0) { "语音音频帧不完整" }
    if (safe == 0f) return ByteArray(bytes.size)
    return bytes.copyOf().also { output ->
        for (i in bytes.indices step 2) {
            val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
            val scaled = (sample * safe).roundToInt().coerceIn(-32768, 32767)
            output[i] = scaled.toByte(); output[i + 1] = (scaled shr 8).toByte()
        }
    }
}
