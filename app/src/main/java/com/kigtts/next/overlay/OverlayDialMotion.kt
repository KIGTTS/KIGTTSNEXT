package com.kigtts.next.overlay

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp

@Stable
internal class OverlayDialMotion(private val point: State<Offset>, private val fraction: State<Float>) {
    val center get() = point.value
    val progress get() = fraction.value
}

/** One timeline moves the visible anchor and changes its shape, including interrupted transitions. */
@Composable
internal fun rememberOverlayDialMotion(placement: OverlayPlacement, expanded: Boolean, dragging: Boolean,
    initialCenter: Offset?, collapsedCenter: Offset?, collapsed: () -> Unit): OverlayDialMotion {
    val progress = remember { mutableFloatStateOf(0f) }
    val center = remember(placement.x, placement.y, placement.width, placement.height, initialCenter) {
        mutableStateOf(initialCenter ?: Offset(placement.fabX, placement.fabY))
    }
    val target = if (expanded) Offset(placement.fabX, placement.fabY)
        else collapsedCenter ?: Offset(placement.fabX, placement.fabY)
    val clock = remember { Animatable(0f) }
    val onCollapsed by rememberUpdatedState(collapsed)
    LaunchedEffect(expanded, dragging, center, target) {
        if (dragging) {
            progress.floatValue = 0f
            center.value = target
            return@LaunchedEffect
        }
        val fromProgress = progress.floatValue
        val fromCenter = center.value
        val endProgress = if (expanded) 1f else 0f
        clock.snapTo(0f)
        clock.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) {
            progress.floatValue = fromProgress + (endProgress - fromProgress) * value
            center.value = if (dragging) target else lerp(fromCenter, target, value)
        }
        if (!expanded) onCollapsed()
    }
    return remember(center) { OverlayDialMotion(center, progress) }
}
