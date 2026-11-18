@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
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
    zoom: (Float) -> Unit, vertical: Boolean, modifier: Modifier = Modifier, bounds: Rect? = null,
    dialVisibilityChanged: (Boolean) -> Unit = {}, adjustment: ProControl? = null,
    presetAccessory: @Composable BoxScope.(visible: Boolean, stops: Int) -> Unit = { _, _ -> },
    adjustmentContent: @Composable (ProControl) -> Unit = {}) {
    val copy = rememberCameraCopy()
    val availableRange = cameraZoomRange(minimum, maximum)
    val range = availableRange ?: (1f..2f)
    val zoomEnabled = enabled && availableRange != null && adjustment == null
    val reported = ratio.takeIf(Float::isFinite)?.coerceIn(range) ?: range.start
    var dragging by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(false) }
    val dialVisible = dial && zoomEnabled
    val latestVisibility by rememberUpdatedState(dialVisibilityChanged)
    LaunchedEffect(dialVisible) { latestVisibility(dialVisible) }
    DisposableEffect(Unit) { onDispose { latestVisibility(false) } }
    var optimistic by remember { mutableFloatStateOf(reported) }
    // CameraX reports zoom asynchronously; keep the dial at the requested value through release.
    val value = if (dial) optimistic else reported
    val latestZoom by rememberUpdatedState(zoom)
    val latestValue by rememberUpdatedState(value)
    val feedback = rememberDragFeedback()
    val stops = remember(availableRange) { availableRange?.let(::cameraZoomStops).orEmpty() }
    val selected = stops.minByOrNull { abs(ln(value / it)) }
    val travel = with(LocalDensity.current) { 260.dp.toPx() }
    LaunchedEffect(dial, dragging) { if (dial && !dragging) { delay(750); dial = false } }
    LaunchedEffect(zoomEnabled) { if (!zoomEnabled) { dragging = false; dial = false; feedback.finish(true) } }
    DisposableEffect(feedback) { onDispose { feedback.finish(true) } }
    fun change(position: Float) {
        optimistic = cameraZoomFromPosition(position, range)
        latestZoom(optimistic)
        feedback.move(position * 600f)
    }
    fun dragModifier() = Modifier.pointerInput(range, vertical, zoomEnabled) {
        if (!zoomEnabled) return@pointerInput
        var position = 0f
        detectDragGestures(onDragStart = {
            optimistic = latestValue; position = cameraZoomPosition(latestValue, range)
            dragging = true; dial = true; feedback.start(position * 600f)
        }, onDragEnd = { dragging = false; feedback.finish() },
            onDragCancel = { dragging = false; feedback.finish(true) }) { pointer, delta ->
            pointer.consume()
            val distance = if (vertical) delta.y else -delta.x
            position = (position + distance / travel).coerceIn(0f, 1f)
            change(position)
        }
    }
    Box(modifier) {
        val alpha by androidx.compose.animation.core.animateFloatAsState(if (dialVisible) 0f else 1f,
            tween(120), label = "zoom-presets-opacity")
        val rail = if (bounds != null) Modifier.align(Alignment.TopStart).cameraChromeBounds(bounds)
            else Modifier.matchParentSize()
        if (availableRange != null || adjustment != null) Box(rail.testTag("camera-zoom-rail"), contentAlignment = Alignment.Center) {
            val available = bounds?.let { if (vertical) it.height.dp else it.width.dp }
            val target = if (adjustment == null) (48 * stops.size).dp else (available ?: 400.dp).coerceAtMost(440.dp)
            val length by animateDpAsState(target, tween(220), label = "camera-zoom-morph")
            val capsule = if (vertical) Modifier.width(48.dp).height(length) else Modifier.width(length).height(48.dp)
            Surface(capsule.alpha(alpha).excludeCameraPull().then(dragModifier()), shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)) {
                AnimatedContent(adjustment, Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(135, delayMillis = 35)) togetherWith fadeOut(tween(115)) },
                    label = "camera-zoom-ruler") { key ->
                    if (key != null) {
                        Box(if (vertical) Modifier.fillMaxSize().verticalModeLabel() else Modifier.fillMaxSize()) {
                            adjustmentContent(key)
                        }
                    } else {
                        val buttons: @Composable () -> Unit = {
                            stops.forEach { factor ->
                                val active = factor == selected
                                CameraZoomButton(if (active) zoomLabel(value) else zoomLabel(factor).removeSuffix("×").removeSuffix(".0"),
                                    copy.text("camera.status_zoom", "value" to zoomLabel(factor)), zoomEnabled, { latestZoom(factor) },
                                    Modifier.testTag("camera-zoom-stop/" + factor.toString().removeSuffix(".0")),
                                    if (active) "camera-zoom-value" else null, selected = active)
                            }
                        }
                        // Don't resize the preset hit targets while the surrounding capsule animates.
                        if (vertical) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { buttons() }
                        else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) { buttons() }
                    }
                }
            }
        }
        presetAccessory(adjustment == null && !dialVisible, stops.size)
        AnimatedVisibility(dialVisible, Modifier.matchParentSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(180))) {
            Box(Modifier.fillMaxSize()) {
            val dialModifier = if (bounds == null) Modifier.fillMaxSize()
                else if (vertical) Modifier.fillMaxHeight().width((bounds.right + 8f).dp)
                else Modifier.fillMaxWidth().height((bounds.bottom + 12f).dp)
            Box(dialModifier.clipToBounds()) {
                CameraZoomDial(value, range, vertical, Modifier.fillMaxSize().testTag("camera-zoom-dial"))
                val touch = if (vertical) Modifier.align(Alignment.CenterEnd).fillMaxWidth(.4f).fillMaxHeight()
                    else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.4f)
                Box(touch.excludeCameraPull().then(dragModifier()).testTag("camera-zoom-slider").semantics {
                    contentDescription = copy.text("camera.zoom_dial")
                    progressBarRangeInfo = ProgressBarRangeInfo(cameraZoomPosition(value, range), 0f..1f)
                    setProgress { change(it); true }
                })
            }
            }
        }
    }
}
