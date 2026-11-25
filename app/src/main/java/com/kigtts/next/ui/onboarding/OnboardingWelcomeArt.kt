package com.kigtts.next.ui.onboarding

import android.graphics.Picture
import android.graphics.Paint
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kigtts.next.i18n.uiText

@Composable
internal fun OnboardingWelcomeArt(modifier: Modifier, active: Boolean, reducedMotion: Boolean, tilt: State<Offset>) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val drawing by WelcomeIllustration.drawings.collectAsState()
    val motion = remember { WelcomeMotion() }
    val description = uiText("onboarding.welcome.art")
    LaunchedEffect(context) { WelcomeIllustration.prepare(context) }
    val present = drawing != null
    LaunchedEffect(present, active, reducedMotion, owner, tilt) {
        if (!present || !active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { frame ->
                val dt = ((frame - previous) / 1_000_000_000.0).toFloat().coerceIn(.001f, .032f)
                previous = frame
                motion.step(dt, tilt.value, reducedMotion)
            }
        }
    }
    val artwork = drawing
    val interactive = artwork?.layers?.isNotEmpty() == true
    Box(modifier.then(if (interactive) Modifier.semantics { contentDescription = description }.interactWithWelcome(motion) else Modifier)) {
        if (artwork != null) Box(Modifier.matchParentSize().graphicsLayer {
            val pose = motion.pose
            alpha = pose.alpha
            val unit = size.width / WELCOME_ART_WIDTH
            translationX = pose.bodyShift.x * unit
            translationY = 18.dp.toPx() * pose.entranceY + pose.bodyShift.y * unit
            rotationZ = pose.bodyRoll
            transformOrigin = TransformOrigin(990f / WELCOME_ART_WIDTH, (1036f + WELCOME_ORIGIN_Y) / WELCOME_ART_HEIGHT)
        }) {
            if (!interactive) WelcomeVector(artwork.still, Modifier.matchParentSize())
            else {
                artwork.layers.forEach { layer ->
                    WelcomeVector(layer.picture, Modifier.matchParentSize().graphicsLayer {
                        val pose = motion.pose
                        val unit = size.width / WELCOME_ART_WIDTH
                        val head = layer.part in headParts
                        if (head) {
                            translationX = pose.head.x * 15f * unit
                            translationY = pose.head.y * 10f * unit
                        }
                        when (layer.part) {
                            WelcomePart.BODY -> Unit
                            WelcomePart.LEFT_HAIR, WelcomePart.RIGHT_HAIR -> Unit
                            WelcomePart.HEAD -> {
                                rotationZ = pose.head.x * .86f + pose.head.y * .14f
                                transformOrigin = welcomePivot(1002f, 564f)
                            }
                            WelcomePart.ARM -> {
                                rotationZ = pose.armRoll
                                transformOrigin = welcomePivot(898f, 720f)
                            }
                            WelcomePart.LEFT_EAR -> {
                                rotationZ = pose.leftEar * .42f
                                transformOrigin = welcomePivot(862f, 86f)
                            }
                            WelcomePart.RIGHT_EAR -> {
                                rotationZ = pose.rightEar * .42f
                                transformOrigin = welcomePivot(1098f, 73f)
                            }
                            WelcomePart.ZIPPER -> {
                                rotationZ = pose.zipper
                                transformOrigin = welcomePivot(1085.5f, 735f)
                            }
                        }
                    }, layer.mesh) { motion.pose.bend(layer.part) }
                }
                WelcomeEyes(artwork.eyes, motion, Modifier.matchParentSize().graphicsLayer {
                    val head = motion.pose.head
                    val unit = size.width / WELCOME_ART_WIDTH
                    translationX = head.x * 15f * unit
                    translationY = head.y * 10f * unit
                    rotationZ = head.x * .86f + head.y * .14f
                    transformOrigin = welcomePivot(1002f, 564f)
                })
            }
        }
    }
}

private val headParts = setOf(WelcomePart.HEAD, WelcomePart.LEFT_EAR, WelcomePart.RIGHT_EAR)
private fun welcomePivot(x: Float, y: Float) = TransformOrigin(x / WELCOME_ART_WIDTH, (y + WELCOME_ORIGIN_Y) / WELCOME_ART_HEIGHT)

private fun WelcomePose.bend(part: WelcomePart): WelcomeBend = when (part) {
    WelcomePart.LEFT_EAR -> WelcomeBend(leftEar * 5.5f, leftStretch)
    WelcomePart.RIGHT_EAR -> WelcomeBend(rightEar * 5.5f, rightStretch)
    WelcomePart.LEFT_HAIR -> WelcomeBend(leftHair)
    WelcomePart.RIGHT_HAIR -> WelcomeBend(rightHair)
    WelcomePart.ARM -> WelcomeBend(armBend)
    else -> WelcomeBend()
}

@Composable
private fun WelcomeVector(picture: Picture, modifier: Modifier, mesh: WelcomeMesh? = null,
    deformation: () -> WelcomeBend = { WelcomeBend() }) {
    val vertices = remember(mesh) { mesh?.newVertices() }
    val paint = remember(mesh) { mesh?.let { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) } }
    Canvas(modifier) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            val mark = canvas.save()
            canvas.scale(size.width / WELCOME_ART_WIDTH, size.height / WELCOME_ART_HEIGHT)
            if (mesh != null && vertices != null && paint != null) mesh.draw(canvas, vertices, paint, deformation())
            else canvas.drawPicture(picture)
            canvas.restoreToCount(mark)
        }
    }
}

@Composable
private fun WelcomeEyes(eyes: List<WelcomeEye>, motion: WelcomeMotion, modifier: Modifier) {
    Canvas(modifier) {
        val pose = motion.pose
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            val mark = canvas.save()
            canvas.scale(size.width / WELCOME_ART_WIDTH, size.height / WELCOME_ART_HEIGHT)
            eyes.forEachIndexed { index, eye ->
                if (pose.squeeze) canvas.drawPicture(eye.squeezed)
                else {
                    val opening = canvas.save()
                    canvas.scale(1f, if (index == 0) pose.eyeOpen else pose.rightEyeOpen, eye.x, eye.y)
                    canvas.drawPicture(eye.white)
                    val clip = canvas.save()
                    canvas.clipPath(eye.aperture)
                    canvas.translate(pose.gaze.x * 10f, pose.gaze.y * 10f)
                    canvas.drawPicture(eye.iris)
                    canvas.restoreToCount(clip)
                    canvas.drawPicture(eye.lash)
                    canvas.restoreToCount(opening)
                }
            }
            canvas.restoreToCount(mark)
        }
    }
}

private fun Modifier.interactWithWelcome(motion: WelcomeMotion) = pointerInput(motion) {
    try {
        awaitPointerEventScope {
            var selected: androidx.compose.ui.input.pointer.PointerId? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = if (selected == null) event.changes.firstOrNull()
                    else event.changes.firstOrNull { it.id == selected }
                if (change == null) continue
                val position = Offset(change.position.x * WELCOME_ART_WIDTH / size.width,
                    change.position.y * WELCOME_ART_HEIGHT / size.height - WELCOME_ORIGIN_Y)
                when {
                    change.changedToDownIgnoreConsumed() && selected == null -> {
                        if (motion.press(position)) { selected = change.id; change.consume() }
                    }
                    change.changedToUpIgnoreConsumed() && selected == change.id -> {
                        motion.release()
                        if (change.type == PointerType.Mouse) motion.hover(position)
                        selected = null
                        change.consume()
                    }
                    change.pressed && selected == change.id -> {
                        motion.drag(position)
                        change.consume()
                    }
                    change.type == PointerType.Mouse -> motion.hover(if (event.type == PointerEventType.Exit) null else position)
                }
            }
        }
    } finally {
        motion.release()
        motion.hover(null)
    }
}
