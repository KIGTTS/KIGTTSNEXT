package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.excludeCameraPull
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val floatingDiameter = 60.dp

// Keeping a portrait-space point makes a right-side landscape dock a 90° counterclockwise rotation.
internal fun cameraShutterToScreen(portrait: Offset, vertical: Boolean): Offset =
    if (vertical) Offset(portrait.y, 1f - portrait.x) else portrait

internal fun cameraShutterToPortrait(screen: Offset, vertical: Boolean): Offset =
    if (vertical) Offset(1f - screen.y, screen.x) else screen

@Stable
internal class CameraFloatingShutterState(position: Offset? = null) {
    var portraitPoint by mutableStateOf(position); private set
    var hoveringDock by mutableStateOf(false); private set
    var interacting by mutableStateOf(false); private set
    var interaction by mutableIntStateOf(0); private set
    var dimmed by mutableStateOf(false); private set
    var workspace: LayoutCoordinates? = null
    var dock: LayoutCoordinates? = null
    private var original: Offset? = null
    private var moving = false

    fun hold(value: Boolean) {
        interacting = value
        interaction++
    }

    fun beginDrag(): Boolean {
        if (moving) return false
        moving = true
        original = portraitPoint
        hold(true)
        return true
    }

    fun drag(point: Offset, vertical: Boolean, radius: Float, returnInset: Float) {
        val space = workspace?.takeIf { it.isAttached } ?: return
        val width = space.size.width.toFloat()
        val height = space.size.height.toFloat()
        if (width <= 0f || height <= 0f || !point.x.isFinite() || !point.y.isFinite()) return
        val xInset = minOf(radius, width / 2f)
        val yInset = minOf(radius, height / 2f)
        val normalized = Offset(point.x.coerceIn(xInset, width - xInset) / width,
            point.y.coerceIn(yInset, height - yInset) / height)
        portraitPoint = cameraShutterToPortrait(normalized, vertical)
        hoveringDock = dock?.takeIf { it.isAttached }?.let {
            val topLeft = space.localPositionOf(it, Offset.Zero)
            val bottomRight = topLeft + Offset(it.size.width.toFloat(), it.size.height.toFloat())
            Rect(topLeft, bottomRight).inflate(returnInset).contains(point)
        } ?: false
    }

    fun endDrag(drop: Boolean) {
        if (!drop) portraitPoint = original else if (hoveringDock) portraitPoint = null
        hoveringDock = false
        original = null
        moving = false
        hold(false)
    }

    suspend fun dimWhenIdle() {
        dimmed = false
        if (!interacting) {
            delay(3_000)
            dimmed = true
        }
    }

    companion object {
        val Saver = listSaver<CameraFloatingShutterState, Float>(
            save = { state -> state.portraitPoint?.let { listOf(it.x, it.y) }.orEmpty() },
            restore = { point -> CameraFloatingShutterState(if (point.size == 2) Offset(point[0], point[1]) else null) },
        )
    }
}

@Composable
internal fun rememberCameraFloatingShutter(visit: String): CameraFloatingShutterState =
    rememberSaveable(visit, saver = CameraFloatingShutterState.Saver) { CameraFloatingShutterState() }

@Composable
internal fun Modifier.cameraFloatingShutterDrag(state: CameraFloatingShutterState, vertical: Boolean,
    dock: Boolean, enabled: Boolean): Modifier {
    val coordinates = remember(state, dock) { arrayOfNulls<LayoutCoordinates>(1) }
    val allowed by rememberUpdatedState(enabled)
    return excludeCameraPull().onGloballyPositioned {
        coordinates[0] = it
        if (dock) state.dock = it
    }.pointerInput(state, vertical, dock) {
        val radius = floatingDiameter.toPx() / 2f
        val returnInset = 8.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (dock && !allowed) return@awaitEachGesture
            if (!dock) state.hold(true)
            var dragging = false
            val space = state.workspace?.takeIf { it.isAttached }
            val anchor = coordinates[0]?.takeIf { it.isAttached }
            try {
                if (space == null || anchor == null) return@awaitEachGesture
                val start = space.localPositionOf(anchor, down.position)
                val grab = if (dock) Offset.Zero else {
                    val center = space.localPositionOf(anchor, Offset(anchor.size.width / 2f, anchor.size.height / 2f))
                    start - center
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed } || !space.isAttached || !anchor.isAttached) break
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            state.drag(space.localPositionOf(anchor, change.position) - grab, vertical, radius, returnInset)
                            state.endDrag(drop = true)
                            dragging = false
                        }
                        break
                    }
                    if (dock && !allowed) break
                    val point = space.localPositionOf(anchor, change.position)
                    if (!dragging && (point - start).getDistance() > viewConfiguration.touchSlop) {
                        dragging = state.beginDrag()
                        if (!dragging) break
                    }
                    if (dragging) {
                        change.consume()
                        state.drag(point - grab, vertical, radius, returnInset)
                    }
                }
            } finally {
                if (dragging) state.endDrag(drop = false)
                if (!dock && state.interacting) state.hold(false)
            }
        }
    }
}

@Composable
internal fun BoxScope.CameraFloatingShutter(state: CameraFloatingShutterState, vertical: Boolean, enabled: Boolean,
    busy: Boolean, countingDown: Boolean, capture: () -> Unit, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.portraitPoint != null } }
    Box(modifier.matchParentSize().onGloballyPositioned { state.workspace = it }) {
        if (visible) BoxWithConstraints(Modifier.matchParentSize()) {
            val workspaceWidth = constraints.maxWidth
            val workspaceHeight = constraints.maxHeight
            LaunchedEffect(state.interaction, state.interacting) { state.dimWhenIdle() }
            val alpha by animateFloatAsState(if (state.dimmed) .5f else 1f, tween(160), label = "temporary shutter idle")
            val scale by animateFloatAsState(if (state.hoveringDock) .78f else 1f, tween(115), label = "temporary shutter docking")
            CompositionLocalProvider(LocalCameraShutterOverlay provides true) {
                CameraShutterButton(enabled, busy, countingDown, {
                    state.hold(false)
                    capture()
                }, Modifier.offset {
                    val point = state.portraitPoint ?: return@offset IntOffset.Zero
                    val center = cameraShutterToScreen(point, vertical)
                    val diameter = floatingDiameter.roundToPx()
                    val left = (center.x * workspaceWidth - diameter / 2f).roundToInt()
                    val top = (center.y * workspaceHeight - diameter / 2f).roundToInt()
                    IntOffset(left.coerceIn(0, (workspaceWidth - diameter).coerceAtLeast(0)),
                        top.coerceIn(0, (workspaceHeight - diameter).coerceAtLeast(0)))
                }.size(floatingDiameter).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
                    .testTag("camera-floating-shutter").cameraFloatingShutterDrag(state, vertical, dock = false, enabled = true),
                    temporary = true)
            }
        }
    }
}
