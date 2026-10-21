@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.floor
import kotlin.math.roundToInt

data class ElasticAction(
    val id: String, val label: String, val icon: ImageVector, val onClick: () -> Unit,
    val iconOnly: Boolean = true, val weight: Float = 1f,
    val busy: Boolean = false,
)

/** The first action stays at the outside edge; excess actions remain reachable in a menu. */
@Composable
fun ElasticButtonGroup(actions: List<ElasticAction>, modifier: Modifier = Modifier, plain: Boolean = false,
    alignEnd: Boolean = false, overflowTag: String = "actions-more", overflowLabel: String = "更多操作") {
    val current by rememberUpdatedState(actions)
    AnimatedActionSet(actions, { listOf(it.id, it.label, it.icon, it.iconOnly) }, modifier,
        alignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) { shown ->
        ElasticButtonStrip(shown.map { action -> action.copy(onClick = { current.firstOrNull { it.id == action.id }?.onClick?.invoke() }) },
            Modifier, plain, alignEnd, overflowTag, overflowLabel)
    }
}

@Composable
private fun ElasticButtonStrip(actions: List<ElasticAction>, modifier: Modifier, plain: Boolean,
    alignEnd: Boolean, overflowTag: String, overflowLabel: String) {
    if (actions.isEmpty()) return
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val preferred = remember(actions.map { it.label to it.iconOnly }, labelStyle, density, measurer) {
        actions.map { if (it.iconOnly) 32f else with(density) {
            measurer.measure(it.label, labelStyle).size.width.toDp().value + 42f
        } }
    }
    var more by remember { mutableStateOf(false) }
    BackHandler(more) { more = false }
    BoxWithConstraints(modifier.height(32.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        val capacity = floor((maxWidth.value + 4f) / 36f).toInt().coerceAtLeast(1)
        val overflow = actions.size > capacity
        val count = if (overflow) (capacity - 1).coerceAtLeast(0) else actions.size
        val hidden = actions.drop(count)
        val allTextFits = !overflow && preferred.sum() + 4f * (actions.size - 1) <= maxWidth.value
        val kept = actions.take(count).let { if (alignEnd) it.reversed() else it }
        val menuAction = ElasticAction(overflowTag, overflowLabel, if (plain) Icons.Default.MoreHoriz else Icons.Default.ExpandMore, { more = true })
        val shown = if (!overflow) kept else if (alignEnd) listOf(menuAction) + kept else kept + menuAction
        val widths = shown.map { action ->
            if (allTextFits) preferred[actions.indexOf(action)] else 32f
        }
        val sources = remember(shown.map { it.id }) { shown.map { MutableInteractionSource() } }
        val pressed = sources.map { it.collectIsPressedAsState().value }
        val animatedWidths = rememberBalancedWidthProvider(widths, sources)
        Layout(modifier = Modifier.width((widths.sum() + 4f * (shown.size - 1)).dp).height(32.dp), content = {
            shown.forEachIndexed { index, action -> key(action.id) {
                val shape = connectedShape(CornerNeighbors(start = index > 0, end = index < shown.lastIndex),
                    neighborPressed = pressed.getOrElse(index - 1) { false } || pressed.getOrElse(index + 1) { false }, outer = 16.dp)
                val button = Modifier.testTag(action.id)
                // A long-press hint can emit a Popup anchor as a second layout node.
                // Each action must still occupy exactly one slot in this measured strip.
                Box(propagateMinConstraints = true) {
                if (action.iconOnly || !allTextFits) ToolButton(action.label, action.icon, action.onClick, button,
                    sources[index], IconButtonDefaults.shapes(shape, RoundedCornerShape(8.dp)),
                    if (plain) compactPlainIconColors() else compactTonalIconColors(), busy = action.busy,
                    iconSize = if (plain) 24.dp else 16.dp)
                else PillButton(action.label, action.icon, action.onClick, button, sources[index],
                    ButtonDefaults.shapes(shape, RoundedCornerShape(8.dp)),
                    if (plain) compactPlainButtonColors() else compactTonalButtonColors())
                }
            } }
        }) { children, constraints ->
            // Press values are read only in measure, so spring frames do not recompose the strip.
            val sizes = animatedWidths()
            val gap = 4.dp.roundToPx()
            val height = constraints.constrainHeight(32.dp.roundToPx())
            val placeables = children.mapIndexed { index, child ->
                child.measure(Constraints.fixed((sizes.getOrElse(index) { 32f } * density.density).roundToInt().coerceAtLeast(0), height))
            }
            layout(constraints.constrainWidth(placeables.sumOf { it.width } + gap * (placeables.size - 1)), height) {
                var x = 0
                placeables.forEach { it.placeRelative(x, 0); x += it.width + gap }
            }
        }
        DropdownMenu(more && overflow && LocalActionInteractivity.current, { more = false }, properties = PopupProperties(focusable = false)) {
            hidden.forEach { action -> DropdownMenuItem(text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, null) }, modifier = Modifier.testTag(action.id + "-menu"),
                onClick = feedbackClick { more = false; action.onClick() }) }
        }
    }
}
