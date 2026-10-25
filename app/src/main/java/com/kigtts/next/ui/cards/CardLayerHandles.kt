package com.kigtts.next.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlin.math.*

/** Controls match the layer's bounds and never enlarge the paper canvas's measured size. */
@Composable
internal fun BoxScope.CardLayerHandles(layer: CardLayer, change: (CardLayer) -> Unit, remove: () -> Unit = {},
    configure: (() -> Unit)? = null, handleActive: (Boolean) -> Unit) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.matchParentSize().onGloballyPositioned { coordinates = it }) {
        fun center(): Offset? = coordinates?.takeIf { it.isAttached }?.let { it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f)) }
        CardDragHandle("缩放并旋转贴纸", Icons.Default.OpenInFull, Modifier.align(Alignment.BottomEnd).testTag("card-layer-scale/" + layer.id),
            layer, ::center, true, change, handleActive)
        MotionIconButton(feedbackClick { change(layer.copy(flipX = !layer.flipX)) },
            Modifier.align(Alignment.TopStart).requiredSize(32.dp).testTag("card-layer-flip-x/" + layer.id),
            colors = IconButtonDefaults.filledTonalIconButtonColors()) { Icon(Icons.Default.Flip, "水平镜像", Modifier.size(18.dp)) }
        MotionIconButton(feedbackClick(remove), Modifier.align(Alignment.TopEnd).requiredSize(32.dp).testTag("card-layer-delete/" + layer.id),
            colors = IconButtonDefaults.filledTonalIconButtonColors()) { Icon(Icons.Default.Close, "删除贴纸", Modifier.size(18.dp)) }
        if (configure != null) MotionIconButton(feedbackClick(configure), Modifier.align(Alignment.BottomStart).requiredSize(32.dp)
            .testTag("card-layer-settings/" + layer.id), colors = IconButtonDefaults.filledTonalIconButtonColors()) {
            Icon(Icons.Default.Settings, "贴纸设置", Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CardDragHandle(label: String, icon: ImageVector, modifier: Modifier, layer: CardLayer,
    center: () -> Offset?, scale: Boolean, change: (CardLayer) -> Unit, handleActive: (Boolean) -> Unit) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val latest = rememberUpdatedState(layer); val update = rememberUpdatedState(change)
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    val corner by animateDpAsState(if (pressed) 8.dp else 24.dp, label = "card-handle-shape")
    fun nudge(value: CardLayer) = if (scale) value.copy(width = (value.width * 1.1).coerceAtMost(2.0))
        else value.copy(rotation = (value.rotation + 15).coerceIn(-36000.0, 36000.0))
    Box(modifier.requiredSize(32.dp).onGloballyPositioned { coordinates = it }
        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(corner))
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
            onClick(if (scale) "放大10%" else "旋转15度") {
                update.value(nudge(latest.value))
                true
            }
        }.pointerInput(layer.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false); down.consume()
                handleActive(true)
                pressed = true; feedback.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                var completed = false; var changed = false; var dragged = false
                val origin = latest.value
                val pivot = center()
                fun point(position: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToRoot(position)?.let { root -> pivot?.let { root - it } }
                val initial = point(down.position)
                var lastAngle = initial?.let { atan2(it.y.toDouble(), it.x.toDouble()) } ?: 0.0
                var angle = origin.rotation
                val radius = initial?.getDistance()?.coerceAtLeast(1f) ?: 1f
                try {
                    do {
                        val event = awaitPointerEvent()
                        val input = event.changes.firstOrNull { it.id == down.id }
                        if (input != null && input.pressed) point(input.position)?.let { vector ->
                            if ((input.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                            val candidate = run {
                                val nextAngle = atan2(vector.y.toDouble(), vector.x.toDouble())
                                var delta = nextAngle - lastAngle
                                if (delta > PI) delta -= 2 * PI; if (delta < -PI) delta += 2 * PI
                                angle += delta * 180 / PI; lastAngle = nextAngle
                                origin.copy(width = (origin.width * vector.getDistance() / radius).coerceIn(.01,
                                    if (origin.kind == CardLayerKind.TEXT) .3 else 2.0), rotation = angle.coerceIn(-36000.0, 36000.0))
                            }
                            if (candidate != origin) { changed = true; update.value(candidate) }
                            input.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    completed = true
                    if (!changed && !dragged) { update.value(nudge(latest.value)); changed = true }
                } finally {
                    pressed = false
                    handleActive(false)
                    if (completed && changed) feedback.value.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
            }
        }, contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}
