package com.kigtts.next.ui.cards

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

internal data class CarouselPhotoPose(val x: Float, val scale: Float, val alpha: Float)

/** Interpolate complete layouts: the two photos exchanging focus always retain their gap. */
internal fun carouselPhotoPose(index: Int, position: Float, widths: List<Float>, viewport: Float): CarouselPhotoPose {
    val page = position.coerceIn(0f, widths.lastIndex.coerceAtLeast(0).toFloat())
    val first = floor(page).toInt()
    val second = min(first + 1, widths.lastIndex)
    val from = carouselRestPose(index, first, widths, viewport)
    if (first == second) return from
    val to = carouselRestPose(index, second, widths, viewport)
    val fraction = page - first
    return CarouselPhotoPose(from.x + (to.x - from.x) * fraction,
        from.scale + (to.scale - from.scale) * fraction, from.alpha + (to.alpha - from.alpha) * fraction)
}

private fun carouselRestPose(index: Int, focus: Int, widths: List<Float>, viewport: Float): CarouselPhotoPose {
    if (index == focus) return CarouselPhotoPose(0f, 1f, 1f)
    val direction = if (index < focus) -1 else 1
    val distance = abs(index - focus)
    val width = widths.getOrElse(index) { 1f }.coerceAtLeast(1f)
    val inner = widths.getOrElse(focus) { 1f } / 2f + 8f
    val available = (viewport / 2f - inner - 8f).coerceAtLeast(1f)
    val peek = min(24f, available * .22f)
    val nearWidth = widths.getOrElse(focus + direction) { width }.coerceAtLeast(1f)
    val nearScale = min(.84f, (available - peek) / nearWidth).coerceAtLeast(.01f)
    val nearOuter = inner + nearWidth * nearScale
    if (distance == 1) return CarouselPhotoPose(direction * (inner + nearWidth * nearScale / 2f), nearScale, 1f)
    // The second photo peeks beyond the first, regardless of their relative widths.
    val farOuter = nearOuter + peek
    val farScale = min(.66f, min(nearScale * .82f, (farOuter - inner) / width)).coerceAtLeast(.01f)
    return CarouselPhotoPose(direction * (farOuter - width * farScale / 2f + (distance - 2) * peek),
        farScale, if (distance == 2) 1f else 0f)
}
