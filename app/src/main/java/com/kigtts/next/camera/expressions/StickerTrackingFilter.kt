package com.kigtts.next.camera.expressions

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.exp

/** Smooth landmark jitter without holding back a face that is moving across the viewfinder. */
internal class StickerTrackingFilter {
    private data class Sample(val face: ExpressionFace, val timeMs: Long)

    private val samples = mutableMapOf<Long, Sample>()

    fun reset() = samples.clear()

    fun update(faces: List<ExpressionFace>, timeMs: Long): List<ExpressionFace> {
        samples.values.removeAll { timeMs - it.timeMs > MAX_GAP_MS }
        val visible = if (faces.size < 2) faces else faces.filterNot { face ->
            faces.any { other -> other !== face && containsSmallFace(other, face) }
        }
        return visible.map { face ->
            val sample = samples[face.id]
            val elapsed = sample?.let { timeMs - it.timeMs }
            val shown = if (sample != null && elapsed != null && elapsed in 1..MAX_GAP_MS) {
                smooth(sample.face, face, elapsed)
            } else face
            samples[face.id] = Sample(shown, timeMs)
            shown
        }
    }

    private fun containsSmallFace(larger: ExpressionFace, smaller: ExpressionFace): Boolean {
        if (larger.opacity < .5f || larger.eyeDistance <= 1f) return false
        val outer = larger.bounds
        val inner = smaller.bounds
        val area = inner.width() * inner.height()
        if (area <= 0f || area > outer.width() * outer.height() * .35f) return false
        if (!outer.contains(inner.centerX(), inner.centerY())) return false
        val overlapX = minOf(outer.right, inner.right) - maxOf(outer.left, inner.left)
        val overlapY = minOf(outer.bottom, inner.bottom) - maxOf(outer.top, inner.top)
        return overlapX * overlapY >= area * .9f
    }

    private fun smooth(previous: ExpressionFace, next: ExpressionFace, elapsedMs: Long): ExpressionFace {
        val a = previous.bounds
        val b = next.bounds
        val movement = maxOf(abs(a.left - b.left), abs(a.top - b.top), abs(a.right - b.right), abs(a.bottom - b.bottom),
            distance(previous.leftEye, next.leftEye), distance(previous.rightEye, next.rightEye),
            distance(previous.nose, next.nose), distance(previous.leftMouth, next.leftMouth), distance(previous.rightMouth, next.rightMouth))
        val speed = movement / maxOf(1f, a.width(), b.width()) * 1000f / elapsedMs
        val motion = ((speed - .35f) / 1.65f).coerceIn(0f, 1f)
        val timeConstant = 110f + (18f - 110f) * motion
        val weight = 1f - exp(-elapsedMs.toFloat() / timeConstant)
        fun mix(a: Float, b: Float) = a + (b - a) * weight
        fun mixPoint(a: ExpressionPoint, b: ExpressionPoint) = ExpressionPoint(mix(a.x, b.x), mix(a.y, b.y))
        return next.copy(
            bounds = RectF(mix(a.left, b.left), mix(a.top, b.top), mix(a.right, b.right), mix(a.bottom, b.bottom)),
            leftEye = mixPoint(previous.leftEye, next.leftEye), rightEye = mixPoint(previous.rightEye, next.rightEye),
            nose = mixPoint(previous.nose, next.nose), leftMouth = mixPoint(previous.leftMouth, next.leftMouth),
            rightMouth = mixPoint(previous.rightMouth, next.rightMouth),
        )
    }

    private fun distance(a: ExpressionPoint, b: ExpressionPoint) = maxOf(abs(a.x - b.x), abs(a.y - b.y))

    private companion object { const val MAX_GAP_MS = 250L }
}
