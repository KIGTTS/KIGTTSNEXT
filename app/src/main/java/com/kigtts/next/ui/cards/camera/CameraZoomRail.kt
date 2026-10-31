package com.kigtts.next.ui.cards.camera

import java.util.Locale
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.abs

internal fun cameraZoomRange(minimum: Float, maximum: Float): ClosedFloatingPointRange<Float>? =
    (minimum..maximum).takeIf { minimum.isFinite() && maximum.isFinite() && minimum > 0f && maximum > minimum }

/** Stops come solely from the bound camera's reported range, never assumed physical lenses. */
internal fun cameraZoomStops(range: ClosedFloatingPointRange<Float>) = (listOf(range.start, 1f, 2f, 5f)).filter { it in range }.distinct().sorted()
internal fun zoomLabel(value: Float): String = String.format(Locale.ROOT, "%.1f×", value)

internal fun cameraZoomPosition(ratio: Float, range: ClosedFloatingPointRange<Float>): Float {
    val value = ratio.coerceIn(range)
    if (range.start < 1f && range.endInclusive > 1f) return if (value <= 1f)
        .25f * (value - range.start) / (1f - range.start)
    else .25f + .75f * ln(value) / ln(range.endInclusive)
    return ln(value / range.start) / ln(range.endInclusive / range.start)
}
internal fun cameraZoomFromPosition(position: Float, range: ClosedFloatingPointRange<Float>): Float {
    val value = position.coerceIn(0f, 1f)
    if (range.start < 1f && range.endInclusive > 1f) {
        if (abs(value - .25f) <= .018f) return 1f
        return if (value < .25f) range.start + (1f - range.start) * value / .25f
        else exp((value - .25f) / .75f * ln(range.endInclusive))
    }
    return (range.start * exp(value * ln(range.endInclusive / range.start))).coerceIn(range)
}
