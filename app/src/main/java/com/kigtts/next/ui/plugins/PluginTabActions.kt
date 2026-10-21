package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

/** Fixed extensions bookend the independently scrolling category tabs. */
@Composable
fun PluginTabActions(actions: List<RegisteredAction>, invoke: (RegisteredAction) -> Unit,
    modifier: Modifier, alignEnd: Boolean, overflowTag: String) {
    val current by rememberUpdatedState(actions)
    val latestInvoke by rememberUpdatedState(invoke)
    AnimatedActionSet(actions, { listOf(it.key, it.action.label, it.action.icon, it.action.buttonStyle) }, modifier,
        alignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) { shown ->
        PluginTabStrip(shown, { action -> current.firstOrNull { it.key == action.key }?.let(latestInvoke) }, alignEnd, overflowTag)
    }
}

@Composable
private fun PluginTabStrip(actions: List<RegisteredAction>, invoke: (RegisteredAction) -> Unit,
    alignEnd: Boolean, overflowTag: String) {
    if (actions.isEmpty()) return
    var more by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.widthIn(max = 72.dp).height(48.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        val capacity = ((maxWidth.value + 4f) / 36f).toInt().coerceAtLeast(1)
        val overflow = actions.size > capacity
        val kept = actions.take(if (overflow) capacity - 1 else capacity)
        val hidden = actions.drop(kept.size)
        val entries = if (alignEnd) kept.reversed() else kept
        val displayed: List<RegisteredAction?> = if (!overflow) entries else if (alignEnd) listOf(null) + entries else entries + listOf(null)
        val sources = remember(displayed.map { it?.key }) { displayed.map { MutableInteractionSource() } }
        val pressed = sources.map { it.collectIsPressedAsState().value }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            displayed.forEachIndexed { index, action -> key(action?.key ?: overflowTag) {
                ElasticTab(false, { if (action == null) more = true else invoke(action) }, sources[index],
                    CornerNeighbors(start = index > 0, end = index < sources.lastIndex),
                    pressed.getOrElse(index - 1) { false } || pressed.getOrElse(index + 1) { false },
                    Modifier.width(32.dp).testTag(action?.key ?: overflowTag)
                        .semantics { contentDescription = action?.action?.label ?: "更多操作" },
                    icon = { Icon(action?.action?.let { it.icon.vector(it.symbol) } ?: Icons.Default.ExpandMore, null, Modifier.size(16.dp)) }, label = {})
            } }
        }
        DropdownMenu(more && overflow && LocalActionInteractivity.current, { more = false }) {
            hidden.forEach { action -> DropdownMenuItem(text = { Text(action.action.label) },
                leadingIcon = { Icon(action.action.icon.vector(action.action.symbol), null) }, modifier = Modifier.testTag(action.key + "-menu"),
                onClick = feedbackClick { more = false; invoke(action) }) }
        }
    }
}
