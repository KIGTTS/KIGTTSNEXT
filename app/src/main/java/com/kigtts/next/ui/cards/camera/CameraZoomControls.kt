@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.navigation.excludeCameraPull
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln

/** Presets stay mounted while the dial is shown, so the active finger never loses its owner. */
@Composable
internal fun CameraZoomRail(ratio: Float, minimum: Float, maximum: Float, enabled: Boolean,
    zoom: (Float) -> Unit, vertical: Boolean, modifier: Modifier = Modifier, bounds: Rect? = null) {
    val range = cameraZoomRange(minimum, maximum) ?: return
    val reported = ratio.takeIf(Float::isFinite)?.coerceIn(range) ?: range.start
    var dragging by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(false) }
    var optimistic by remember { mutableFloatStateOf(reported) }
    // CameraX reports zoom asynchronously; keep the dial at the requested value through release.
    val value = if (dial) optimistic else reported
    val latestZoom by rememberUpdatedState(zoom)
    val latestValue by rememberUpdatedState(value)
    val feedback = rememberDragFeedback()
    val stops = remember(range) { cameraZoomStops(range) }
    val selected = stops.minBy { abs(ln(value / it)) }
    val travel = with(LocalDensity.current) { 260.dp.toPx() }
    LaunchedEffect(dial, dragging) { if (dial && !dragging) { delay(750); dial = false } }
    LaunchedEffect(enabled) { if (!enabled) { dragging = false; dial = false; feedback.finish(true) } }
    DisposableEffect(feedback) { onDispose { feedback.finish(true) } }
    fun change(position: Float) {
        optimistic = cameraZoomFromPosition(position, range)
        latestZoom(optimistic)
        feedback.move(position * 600f)
    }
    fun dragModifier(wheel: Boolean) = Modifier.pointerInput(range, vertical, enabled, wheel) {
        if (!enabled) return@pointerInput
        var position = 0f
        detectDragGestures(onDragStart = {
            optimistic = latestValue; position = cameraZoomPosition(latestValue, range)
            dragging = true; dial = true; feedback.start(position * 600f)
        }, onDragEnd = { dragging = false; feedback.finish() },
            onDragCancel = { dragging = false; feedback.finish(true) }) { pointer, delta ->
            pointer.consume()
            val distance = if (wheel) { if (vertical) delta.x else -delta.y }
                else if (vertical) -delta.y else delta.x
            position = (position + distance / travel).coerceIn(0f, 1f)
            change(position)
        }
    }
    BoxWithConstraints(modifier) {
        val alpha by androidx.compose.animation.core.animateFloatAsState(if (dial) 0f else 1f,
            tween(120), label = "zoom-presets-opacity")
        val rail = if (bounds != null) Modifier.align(Alignment.TopStart).cameraChromeBounds(bounds)
            else Modifier.matchParentSize()
        Box(rail.testTag("camera-zoom-rail"), contentAlignment = Alignment.Center) {
            Surface(Modifier.alpha(alpha).excludeCameraPull().then(dragModifier(false)), shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)) {
                val buttons: @Composable () -> Unit = {
                    stops.forEach { factor ->
                        val active = factor == selected
                        CameraZoomButton(if (active) zoomLabel(value) else zoomLabel(factor).removeSuffix("×").removeSuffix(".0"),
                            "变焦 ${zoomLabel(factor)}", enabled, { latestZoom(factor) },
                            Modifier.testTag("camera-zoom-stop/" + factor.toString().removeSuffix(".0")),
                            if (active) "camera-zoom-value" else null, selected = active)
                    }
                }
                // Opacity keeps the original touch owner and geometry throughout a drag.
                if (vertical) Column(Modifier.padding(vertical = 4.dp)) { buttons() }
                else Row(Modifier.padding(horizontal = 4.dp)) { buttons() }
            }
        }
        AnimatedVisibility(dial && enabled, Modifier.matchParentSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(180))) {
            Box {
                CameraZoomDial(value, range, vertical, Modifier.fillMaxSize().testTag("camera-zoom-dial"))
                val touch = if (vertical) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.45f)
                    else Modifier.align(Alignment.CenterEnd).fillMaxWidth(.48f).fillMaxHeight(.7f)
                Box(touch.excludeCameraPull().then(dragModifier(true)).testTag("camera-zoom-slider").semantics {
                    contentDescription = "变焦轮盘"
                    progressBarRangeInfo = ProgressBarRangeInfo(cameraZoomPosition(value, range), 0f..1f)
                    setProgress { change(it); true }
                })
            }
        }
    }
}
