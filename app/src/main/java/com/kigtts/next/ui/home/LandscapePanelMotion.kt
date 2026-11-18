@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

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
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.components.AnchorFeedback
import kotlinx.coroutines.launch

@Stable
class LandscapePanelMotion(private val widthState: State<Float>, private val columnState: State<Int>,
    private val visibleState: State<Boolean>, val geometry: LandscapePanelGeometry,
    val dragModifier: Modifier, private val draggingState: State<Boolean>, private val press: DragHandlePress) {
    val width: Float get() = widthState.value
    val columns: Int get() = columnState.value
    val visible: Boolean get() = visibleState.value
    val active: Boolean get() = draggingState.value || press.pressed
}

@Composable
fun rememberLandscapePanel(state: AppState, model: AppViewModel, availableWidth: Float): LandscapePanelMotion {
    val geometry = remember(availableWidth) { LandscapePanelGeometry(availableWidth) }
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val feedback = rememberDragFeedback()
    val snapFeedback = remember(feedback) { AnchorFeedback(feedback) }
    val latestFeedback by rememberUpdatedState(feedback)
    val latestGeometry by rememberUpdatedState(geometry)
    val target = if (state.panel == null) LandscapePanelGeometry.COLLAPSED
        else geometry.widthFor(state.panelColumns.coerceAtMost(geometry.maximumColumns))
    val animation = remember { Animatable(target) }
    val dragging = remember { mutableStateOf(false) }
    val rawWidth = remember { mutableFloatStateOf(target) }
    val startedClosed = remember { mutableStateOf(false) }
    val latestPanel = rememberUpdatedState(state.panel)
    val width = remember { derivedStateOf {
        if (dragging.value) latestGeometry.damp(rawWidth.floatValue) else animation.value.coerceIn(LandscapePanelGeometry.COLLAPSED, latestGeometry.maximum)
    } }
    val columns = remember { derivedStateOf { latestGeometry.columnsAt(width.value) } }
    val visible = remember { derivedStateOf { latestPanel.value != null || width.value > LandscapePanelGeometry.COLLAPSED + 1f } }
    val settleMotion = tween<Float>(260, easing = FastOutSlowInEasing)
    LaunchedEffect(target, dragging.value) {
        if (dragging.value) animation.stop() else animation.animateTo(target, settleMotion)
    }
    val start = remember(model) { {
        if (!dragging.value) {
            startedClosed.value = model.state.value.panel == null
            rawWidth.floatValue = width.value
            dragging.value = true
            latestFeedback.start(rawWidth.floatValue)
            snapFeedback.start(rawWidth.floatValue, latestGeometry.anchors)
            if (startedClosed.value) model.openPanel(HomePanel.QUICK_TEXT)
        }
    } }
    val move = remember(start, density) { { delta: Float ->
        start()
        rawWidth.floatValue = (rawWidth.floatValue - delta / density)
            .coerceIn(LandscapePanelGeometry.COLLAPSED, latestGeometry.maximum)
        snapFeedback.move(rawWidth.floatValue, latestGeometry.anchors)
    } }
    val settle = remember(model, scope, density) { { velocity: Float ->
        if (dragging.value) {
            latestFeedback.finish()
            val current = width.value
            val nearest = latestGeometry.settle(current, velocity / density)
            val restored = latestGeometry.widthFor(model.state.value.panelColumns.coerceAtMost(latestGeometry.maximumColumns))
            val anchor = if (startedClosed.value && current > LandscapePanelGeometry.COLLAPSED + 12f) maxOf(restored, nearest) else nearest
            scope.launch {
                animation.snapTo(current)
                dragging.value = false
                if (anchor == LandscapePanelGeometry.COLLAPSED) model.closePanel()
                else if (!startedClosed.value || nearest > restored) model.panelColumns(latestGeometry.columnsAt(anchor))
            }
        }
    } }
    val press = rememberDragHandlePress(feedback, { width.value }, { dragging.value })
    val description = uiText("accessibility.quick_text_columns")
    val drag = press.modifier.draggable(rememberDraggableState(move), Orientation.Horizontal,
        onDragStarted = { start() }, onDragStopped = { settle(it) }).semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(if (visible.value) columns.value.toFloat() else 0f, 0f..geometry.maximumColumns.toFloat())
        setProgress {
            val count = it.toInt().coerceIn(0, latestGeometry.maximumColumns)
            if (count == 0) model.closePanel() else {
                model.panelColumns(count)
                if (model.state.value.panel == null) model.openPanel(HomePanel.QUICK_TEXT)
            }
            true
        }
    }
    return remember(width, columns, visible, geometry, drag, press) { LandscapePanelMotion(width, columns, visible, geometry, drag, dragging, press) }
}
