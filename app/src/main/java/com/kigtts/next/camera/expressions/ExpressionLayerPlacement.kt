package com.kigtts.next.camera.expressions

import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Shared geometry for the camera renderer and the editor's transform handles. */
internal data class ExpressionLayerPlacement(val anchor: ExpressionPoint, val offset: ExpressionPoint,
    val faceRoll: Float, val rotation: Float, val edge: Float, val pivotY: Float) {
    val angle get() = faceRoll + rotation
    val center get() = anchor + offset.rotated(faceRoll) + ExpressionPoint(0f, edge * (.5f - pivotY)).rotated(angle)
    fun bounds() = RectF(-edge / 2f, -edge * pivotY, edge / 2f, edge * (1f - pivotY))

    fun contains(point: ExpressionPoint, minimumEdge: Float): Boolean {
        val local = (point - center).rotated(-angle)
        val half = maxOf(edge, minimumEdge) / 2f
        return local.x in -half..half && local.y in -half..half
    }
}

internal fun ExpressionLayer.placement(face: ExpressionFace): ExpressionLayerPlacement {
    val width = face.bounds.width()
    val eyeDistance = face.eyeDistance
    val point = when (anchor) {
        ExpressionAnchor.FACE -> ExpressionPoint(face.bounds.centerX(), face.bounds.centerY())
        ExpressionAnchor.EYES -> face.eyeCenter
        ExpressionAnchor.LEFT_EYE -> face.leftEye
        ExpressionAnchor.RIGHT_EYE -> face.rightEye
        ExpressionAnchor.MOUTH -> face.mouthCenter
        ExpressionAnchor.NOSE -> face.nose
        ExpressionAnchor.FOREHEAD -> face.eyeCenter.let { ExpressionPoint(it.x, it.y - eyeDistance * .65f) }
    }
    val base = when (anchor) {
        ExpressionAnchor.FACE, ExpressionAnchor.FOREHEAD -> width
        ExpressionAnchor.EYES -> eyeDistance * 2f
        ExpressionAnchor.LEFT_EYE, ExpressionAnchor.RIGHT_EYE, ExpressionAnchor.NOSE -> eyeDistance * .72f
        ExpressionAnchor.MOUTH -> eyeDistance * 1.4f
    }
    // The original eye artwork uses row 99 as the reference line.
    val pivot = pivotY ?: if (anchor == ExpressionAnchor.EYES) 99f / 320f else .5f
    return ExpressionLayerPlacement(point, ExpressionPoint(width * x, width * y), face.roll, rotation, base * scale, pivot)
}

internal fun ExpressionLayer.transform(face: ExpressionFace, pan: ExpressionPoint, zoom: Float, turn: Float): ExpressionLayer {
    if (!pan.x.isFinite() || !pan.y.isFinite() || !zoom.isFinite() || zoom <= 0f || !turn.isFinite()) return this
    val width = face.bounds.width()
    if (width <= 0f) return this
    val angle = ((rotation + turn + 180f) % 360f + 360f) % 360f - 180f
    val next = copy(scale = (scale * zoom).coerceIn(.15f, 3f), rotation = angle)
    val shift = (placement(face).center - next.placement(face).center + pan).rotated(-face.roll)
    return next.copy(x = (x + shift.x / width).coerceIn(-1f, 1f), y = (y + shift.y / width).coerceIn(-1f, 1f))
}

private operator fun ExpressionPoint.plus(other: ExpressionPoint) = ExpressionPoint(x + other.x, y + other.y)
private operator fun ExpressionPoint.minus(other: ExpressionPoint) = ExpressionPoint(x - other.x, y - other.y)
private fun ExpressionPoint.rotated(degrees: Float): ExpressionPoint {
    val angle = (degrees * PI / 180).toFloat()
    val cosine = cos(angle)
    val sine = sin(angle)
    return ExpressionPoint(x * cosine - y * sine, x * sine + y * cosine)
}
