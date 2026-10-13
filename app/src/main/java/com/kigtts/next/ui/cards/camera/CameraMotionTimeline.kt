package com.kigtts.next.ui.cards.camera

/** Clock mapping keeps the actual recording gap between segments, never speeds up captured motion. */
internal data class CameraMotionWindow(val startMs: Long, val endMs: Long) {
    fun intersects(part: CameraMotionPart) = part.startedAtMs < endMs && part.startedAtMs + part.durationMs > startMs
    fun localStartUs(part: CameraMotionPart) = (startMs - part.startedAtMs).coerceAtLeast(0) * 1000
    fun absoluteUs(part: CameraMotionPart, sampleUs: Long) = part.startedAtMs * 1000 + sampleUs
}

internal fun cameraMotionWindow(shutterAtMs: Long, parts: List<CameraMotionPart>): CameraMotionWindow {
    require(shutterAtMs >= 0 && parts.isNotEmpty() && parts.size <= 8) { "实况片段不存在" }
    require(parts.all { it.startedAtMs >= 0 && it.durationMs in 1..30_000 }) { "实况片段时间无效" }
    val start = maxOf(0, shutterAtMs - 2000)
    val end = minOf(shutterAtMs + 1200, parts.maxOf { it.startedAtMs + it.durationMs })
    require(end > shutterAtMs && parts.any { it.startedAtMs <= shutterAtMs && it.startedAtMs + it.durationMs >= shutterAtMs }) {
        "实况片段未覆盖快门时刻"
    }
    return CameraMotionWindow(start, end)
}
