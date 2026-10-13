package com.kigtts.next.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

internal data class PolaroidPaint(val width: Float, val centerX: Float, val centerY: Float)

private val paintConverter = TwoWayConverter<PolaroidPaint, AnimationVector3D>(
    convertToVector = { AnimationVector3D(it.width, it.centerX, it.centerY) },
    convertFromVector = { PolaroidPaint(it.v1, it.v2, it.v3) })

internal class PolaroidResizeMotion(width: Float, viewportWidth: Float, viewportHeight: Float) {
    var endpoint = PolaroidPaint(width, viewportWidth / 2f, viewportHeight / 2f)
    val painted = Animatable(endpoint, paintConverter)
}

/** Measure only at the new endpoint; interpolate the rendered paper without laying out its content every frame. */
@Composable
internal fun rememberPolaroidResize(id: String, width: Dp, viewportWidth: Dp, viewportHeight: Dp,
    coordinateSpace: Boolean? = null): PolaroidResizeMotion {
    // Rotation defines a new coordinate system. Old absolute centers can be outside the new
    // short viewport; ordinary same-axis dock resizing still retains its continuous trajectory.
    val motion = remember(id, coordinateSpace) { PolaroidResizeMotion(width.value, viewportWidth.value, viewportHeight.value) }
    // BoxWithConstraints subcomposes during measurement: the new paper is measured before
    // LaunchedEffect starts. Its first layer must already compensate for that new endpoint.
    // Animate absolute painted geometry; an interrupted transition never resets its pixels.
    val endpoint = PolaroidPaint(width.value, viewportWidth.value / 2f, viewportHeight.value / 2f)
    motion.endpoint = endpoint
    LaunchedEffect(endpoint) {
        if (motion.painted.value != endpoint) motion.painted.animateTo(endpoint, tween(240, easing = FastOutSlowInEasing))
    }
    return motion
}

internal fun Modifier.polaroidResizeLayer(motion: PolaroidResizeMotion): Modifier = graphicsLayer {
    val painted = motion.painted.value
    val endpoint = motion.endpoint
    scaleX = painted.width / endpoint.width.coerceAtLeast(1f)
    scaleY = scaleX
    translationX = (painted.centerX - endpoint.centerX) * density
    translationY = (painted.centerY - endpoint.centerY) * density
}
