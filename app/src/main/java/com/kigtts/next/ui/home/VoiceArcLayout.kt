package com.kigtts.next.ui.home

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

internal data class VoiceArcPose(val center: Offset, val radius: Float, val start: Float, val end: Float,
    val thickness: Float, val bounds: Rect)

/** Rounded segments of one circular band, concentric with the send area. */
internal fun voiceArcPoses(width: Float, weights: List<Float>): List<VoiceArcPose> {
    val radius = width * 1.35f
    val center = Offset(width / 2f, radius + 42f)
    val thickness = min(64f, (width - 16f) / weights.size.coerceAtLeast(1) - 8f).coerceAtLeast(30f)
    val unit = (width - 16f) / weights.sum().coerceAtLeast(1f)
    var left = 8f
    return weights.map { weight ->
        val span = unit * weight
        val from = left + thickness / 2f + 3f
        val to = (left + span - thickness / 2f - 3f).coerceAtLeast(from)
        left += span
        fun angle(x: Float) = -PI.toFloat() / 2 + asin(((x - center.x) / radius).coerceIn(-1f, 1f))
        val start = angle(from); val end = angle(to)
        val half = thickness / 2f
        val pose = VoiceArcPose(center, radius, start, end, thickness, Rect.Zero)
        // The smallest corner contains all larger corners, including the resting semicircular caps.
        val points = voiceArcCurves(pose, min(12f / half, 1f)).flatMap { curve ->
            val to = curve.from + curve.sweep
            val angles = mutableListOf(curve.from, to)
            val quarter = PI.toFloat() / 2f
            for (i in floor(min(curve.from, to) / quarter).toInt()..ceil(max(curve.from, to) / quarter).toInt()) {
                val a = i * quarter
                if (a in min(curve.from, to)..max(curve.from, to)) angles += a
            }
            angles.map { a -> curve.center + Offset(curve.radius * cos(a), curve.radius * sin(a)) }
        }
        pose.copy(bounds = Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }))
    }
}
private data class VoiceArcCurve(val center: Offset, val radius: Float, val from: Float, val sweep: Float)

private fun voiceArcCurves(pose: VoiceArcPose, roundness: Float): List<VoiceArcCurve> {
    val half = pose.thickness / 2f
    val corner = (half * roundness).coerceIn(.01f, half)
    val outer = pose.radius + half; val inner = pose.radius - half
    val start = pose.start - asin(half / pose.radius); val end = pose.end + asin(half / pose.radius)
    val outerAngle = asin(corner / (outer - corner)); val innerAngle = asin(corner / (inner + corner))
    val quarter = PI.toFloat() / 2f
    fun radial(radius: Float, angle: Float) = pose.center + Offset(radius * cos(angle), radius * sin(angle))
    return listOf(
        VoiceArcCurve(pose.center, outer, start + outerAngle, end - start - 2f * outerAngle),
        VoiceArcCurve(radial(outer - corner, end - outerAngle), corner, end - outerAngle, quarter + outerAngle),
        VoiceArcCurve(radial(inner + corner, end - innerAngle), corner, end + quarter, quarter - innerAngle),
        VoiceArcCurve(pose.center, inner, end - innerAngle, start - end + 2f * innerAngle),
        VoiceArcCurve(radial(inner + corner, start + innerAngle), corner, start + innerAngle + 2f * quarter, quarter - innerAngle),
        VoiceArcCurve(radial(outer - corner, start + outerAngle), corner, start - quarter, quarter + outerAngle))
}

/** The same annular outline throughout the animation, with four genuinely circular fillets. */
internal class VoiceArcSegment(private val pose: VoiceArcPose, private val landscape: Boolean, private val roundness: Float = 1f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val quarter = PI.toFloat() / 2f
        fun local(point: Offset): Offset {
            val delta = point - pose.bounds.topLeft
            return if (landscape) Offset(delta.y / pose.bounds.height * size.width,
                (1f - delta.x / pose.bounds.width) * size.height)
            else Offset(delta.x / pose.bounds.width * size.width, delta.y / pose.bounds.height * size.height)
        }
        val path = Path()
        fun arc(center: Offset, radius: Float, from: Float, sweep: Float) {
            val steps = ceil(abs(sweep) / quarter).toInt().coerceAtLeast(1)
            val step = sweep / steps
            repeat(steps) { index ->
                val a = from + step * index; val b = a + step
                val k = 4f / 3f * tan(step / 4f)
                val p1 = local(center + Offset(radius * (cos(a) - k * sin(a)), radius * (sin(a) + k * cos(a))))
                val p2 = local(center + Offset(radius * (cos(b) + k * sin(b)), radius * (sin(b) - k * cos(b))))
                val p3 = local(center + Offset(radius * cos(b), radius * sin(b)))
                path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
        }
        voiceArcCurves(pose, roundness).forEachIndexed { index, curve ->
            val start = local(curve.center + Offset(curve.radius * cos(curve.from), curve.radius * sin(curve.from)))
            if (index == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
            arc(curve.center, curve.radius, curve.from, curve.sweep)
        }
        path.close()
        return Outline.Generic(path)
    }
}
