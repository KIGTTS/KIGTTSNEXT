package com.kigtts.next.ui.sketch

import android.graphics.Path
import androidx.compose.ui.geometry.Offset

/** One filled shape per stroke avoids dark seams in translucent highlighter ink. */
fun strokePath(stroke: Stroke, width: Float, height: Float): Path {
    val path = Path()
    val shorter = minOf(width, height)
    fun pixel(point: StrokePoint) = Offset(point.position.x * width, point.position.y * height)
    stroke.points.forEach { point ->
        val p = pixel(point)
        path.addCircle(p.x, p.y, point.width * shorter / 2f, Path.Direction.CW)
    }
    stroke.points.zipWithNext().forEach { (first, second) ->
        val a = pixel(first); val b = pixel(second)
        val delta = b - a
        val distance = delta.getDistance()
        if (distance > .001f) {
            val normal = Offset(-delta.y, delta.x) / distance
            val aRadius = normal * (first.width * shorter / 2f)
            val bRadius = normal * (second.width * shorter / 2f)
            path.moveTo(a.x - aRadius.x, a.y - aRadius.y)
            path.lineTo(b.x - bRadius.x, b.y - bRadius.y)
            path.lineTo(b.x + bRadius.x, b.y + bRadius.y)
            path.lineTo(a.x + aRadius.x, a.y + aRadius.y)
            path.close()
        }
    }
    return path
}

private fun distanceToSegment(point: Offset, a: Offset, b: Offset): Float {
    val delta = b - a
    val squared = delta.x * delta.x + delta.y * delta.y
    if (squared < .0001f) return (point - a).getDistance()
    val relative = point - a
    val t = ((relative.x * delta.x + relative.y * delta.y) / squared).coerceIn(0f, 1f)
    return (point - (a + delta * t)).getDistance()
}

private fun crosses(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    fun cross(x: Offset, y: Offset) = x.x * y.y - x.y * y.x
    val ab = b - a; val cd = d - c
    val denominator = cross(ab, cd)
    if (kotlin.math.abs(denominator) < .0001f) return false
    val t = cross(c - a, cd) / denominator
    val u = cross(c - a, ab) / denominator
    return t in 0f..1f && u in 0f..1f
}

fun strokeIntersects(stroke: Stroke, from: Offset, to: Offset, radius: Float, width: Float, height: Float): Boolean {
    fun pixel(point: StrokePoint) = Offset(point.position.x * width, point.position.y * height)
    val shorter = minOf(width, height)
    if (stroke.points.size == 1) {
        val point = stroke.points.single()
        return distanceToSegment(pixel(point), from, to) <= radius + point.width * shorter / 2f
    }
    return stroke.points.zipWithNext().any { (first, second) ->
        val a = pixel(first); val b = pixel(second)
        val distance = if (crosses(a, b, from, to)) 0f else minOf(
            distanceToSegment(a, from, to), distanceToSegment(b, from, to),
            distanceToSegment(from, a, b), distanceToSegment(to, a, b))
        distance <= radius + maxOf(first.width, second.width) * shorter / 2f
    }
}
