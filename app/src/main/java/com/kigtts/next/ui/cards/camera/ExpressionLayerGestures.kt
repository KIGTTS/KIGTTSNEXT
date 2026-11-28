package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.ExpressionLayer
import com.kigtts.next.camera.expressions.ExpressionPoint
import com.kigtts.next.camera.expressions.placement
import com.kigtts.next.camera.expressions.transform
import kotlin.math.PI
import kotlin.math.abs

internal fun Modifier.expressionLayerGestures(effectId: String, layers: State<List<ExpressionLayer>>,
    select: State<(Int) -> Unit>, change: State<(Int, ExpressionLayer) -> Unit>) = pointerInput(effectId) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (size.width <= 0) return@awaitEachGesture
        val factor = EXPRESSION_PREVIEW_EDGE.toFloat() / size.width
        val point = ExpressionPoint(down.position.x * factor, down.position.y * factor)
        val current = layers.value
        val target = current.indices.reversed().firstOrNull {
            current[it].placement(expressionPreviewFace).contains(point, 44.dp.toPx() * factor)
        } ?: return@awaitEachGesture
        select.value(target)
        var layer = current[target]
        var editing = false
        var travel = Offset.Zero
        var zoom = 1f
        var turn = 0f
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break
            val panChange = event.calculatePan()
            val zoomChange = event.calculateZoom()
            val turnChange = event.calculateRotation()
            if (!editing) {
                travel += panChange
                zoom *= zoomChange
                turn += turnChange
                val radius = event.calculateCentroidSize(useCurrent = false)
                val motion = maxOf(travel.getDistance(), abs(zoom - 1f) * radius,
                    abs(turn) * (PI / 180).toFloat() * radius)
                editing = motion > viewConfiguration.touchSlop
            }
            if (editing && (panChange != Offset.Zero || zoomChange != 1f || turnChange != 0f)) {
                layer = layer.transform(expressionPreviewFace,
                    ExpressionPoint(panChange.x * factor, panChange.y * factor), zoomChange, turnChange)
                change.value(target, layer)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
