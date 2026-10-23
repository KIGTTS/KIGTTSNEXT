@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.kigtts.next.state.*
import com.kigtts.next.state.HomePanelSizes.COLLAPSED
import kotlinx.coroutines.launch
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.components.AnchorFeedback
import kotlin.math.abs

@Stable
class HomeSheetMotion(
    private val heightState: State<Float>, private val panelState: State<HomePanel?>,
    val maximum: Float, val dragModifier: Modifier,
    val moveBy: (Float) -> Float, val settle: (Float) -> Unit, private val draggingState: State<Boolean>,
    private val press: DragHandlePress,
) {
    val height: Float get() = heightState.value
    val panel: HomePanel? get() = panelState.value
    val dragging: Boolean get() = draggingState.value
    val active: Boolean get() = press.pressed || dragging
}

@Composable
fun rememberHomeSheet(state: AppState, model: AppViewModel, availableHeight: Float): HomeSheetMotion {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val feedback = rememberDragFeedback()
    val latestFeedback by rememberUpdatedState(feedback)
    val geometry = remember(availableHeight) { PortraitPanelGeometry(availableHeight) }
    val maximum = geometry.maximum
    val latestGeometry by rememberUpdatedState(geometry)
    val snapFeedback = remember(feedback) { AnchorFeedback(feedback) }
    val animation = remember { Animatable(if (state.panel == null) COLLAPSED else state.panelHeight) }
    val dragging = remember { mutableStateOf(false) }
    val dragHeight = remember { mutableFloatStateOf(COLLAPSED) }
    val startedClosed = remember { mutableStateOf(false) }
    val retainedPanel = remember { mutableStateOf<HomePanel?>(state.panel) }
    val currentPanel = rememberUpdatedState(state.panel)
    val height = remember { derivedStateOf { if (dragging.value) dragHeight.floatValue else animation.value } }
    val visiblePanel = remember { derivedStateOf {
        currentPanel.value ?: retainedPanel.value.takeIf { height.value > COLLAPSED + 1f }
    } }
    LaunchedEffect(state.panel) { if (state.panel != null) retainedPanel.value = state.panel }
    val target = if (state.panel == null) COLLAPSED else geometry.nearest(state.panelHeight.coerceIn(COLLAPSED, maximum))
    val settleMotion = tween<Float>(260, easing = FastOutSlowInEasing)
    LaunchedEffect(target, dragging.value) {
        if (dragging.value) animation.stop() else animation.animateTo(target, settleMotion)
    }
    val start = remember(model, maximum) { {
        if (!dragging.value) {
            startedClosed.value = model.state.value.panel == null
            dragHeight.floatValue = height.value.coerceIn(COLLAPSED, maximum)
            dragging.value = true
            latestFeedback.start(dragHeight.floatValue)
            snapFeedback.start(dragHeight.floatValue, latestGeometry.anchors)
            if (startedClosed.value) model.openPanel(HomePanel.QUICK_TEXT, dragHeight = COLLAPSED)
        }
    } }
    val move = remember(start, density, maximum) { { delta: Float ->
        start()
        val before = dragHeight.floatValue
        val after = (before - delta / density).coerceIn(COLLAPSED, maximum)
        dragHeight.floatValue = after
        snapFeedback.move(after, latestGeometry.anchors)
        (before - after) * density
    } }
    val settle = remember(model, maximum, scope) { { velocity: Float ->
        if (dragging.value) {
            latestFeedback.finish()
            val current = dragHeight.floatValue
            val restored = latestGeometry.nearest(model.state.value.lastExpandedPanelHeight.coerceIn(HomePanelSizes.PEEK, maximum))
            val nearest = latestGeometry.settle(current, velocity)
            val anchor = when {
                current <= COLLAPSED + 12f -> COLLAPSED
                startedClosed.value -> maxOf(restored, nearest)
                else -> nearest
            }
            scope.launch {
                // Continue from the finger without overshooting the subtitle viewport.
                animation.snapTo(current)
                dragging.value = false
                if (anchor == COLLAPSED) model.closePanel() else model.panelHeight(anchor)
            }
        }
    } }
    val press = rememberDragHandlePress(feedback, { height.value }, { dragging.value }, feedbackOnPress = state.panel != null)
    val drag = press.modifier.draggable(state = rememberDraggableState { move(it) }, orientation = Orientation.Vertical,
        onDragStarted = { start() }, onDragStopped = { settle(it) }).semantics {
        contentDescription = "调整底部面板高度"
        progressBarRangeInfo = ProgressBarRangeInfo(height.value, COLLAPSED..maximum)
        setProgress { value ->
            if (value <= COLLAPSED) model.closePanel()
            else {
                if (model.state.value.panel == null) model.openPanel(HomePanel.QUICK_TEXT)
                model.panelHeight(latestGeometry.nearest(value.coerceIn(COLLAPSED, maximum)))
            }
            true
        }
    }
    return remember(height, visiblePanel, maximum, drag, press) { HomeSheetMotion(height, visiblePanel, maximum, drag, move, settle, dragging, press) }
}
