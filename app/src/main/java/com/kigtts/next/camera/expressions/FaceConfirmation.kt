package com.kigtts.next.camera.expressions

import android.graphics.RectF

private const val MIN_FACE_FRACTION = .055f
internal const val FACE_CONFIRM_MS = 120L
internal const val FACE_HOLD_MS = 180L
private const val MAX_SIGHTING_GAP_MS = 320L

internal fun faceCanTrack(face: ExpressionFace, bounds: RectF, width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0 || face.confidence < .5f || face.opacity < .65f) return false
    val minimum = minOf(width, height) * MIN_FACE_FRACTION
    return bounds.width() >= minimum && bounds.height() >= minimum &&
        bounds.left < width && bounds.top < height && bounds.right > 0f && bounds.bottom > 0f
}

/** Require repeated sightings over an elapsed-time threshold, regardless of analysis FPS. */
internal class FaceConfirmation {
    private data class Sighting(val first: Long, var last: Long, var count: Int)
    private val sightings = mutableMapOf<Long, Sighting>()

    fun update(ids: List<Long>, at: Long): Set<Long> {
        sightings.entries.removeAll { at - it.value.last > MAX_SIGHTING_GAP_MS }
        return ids.filterTo(mutableSetOf()) { id ->
            val sighting = sightings.getOrPut(id) { Sighting(at, at, 1) }
            if (at > sighting.last) { sighting.last = at; sighting.count++ }
            sighting.count >= 2 && at - sighting.first >= FACE_CONFIRM_MS
        }
    }

    fun clear() = sightings.clear()
}
