package com.kigtts.next.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** The rail seeks the complete metadata timeline, without decoding intervening photos. */
@Composable internal fun AlbumTimeline(state: LazyGridState, dates: List<LocalDate>, dateLabel: (LocalDate) -> String,
    modifier: Modifier = Modifier) {
    if (dates.isEmpty()) return
    var dragging by remember(state, dates) { mutableStateOf(false) }
    var target by remember(state, dates) { mutableIntStateOf(0) }
    val scrollable by remember(state) { derivedStateOf { state.canScrollBackward || state.canScrollForward } }
    if (!scrollable && !dragging) return
    val index by remember(state, dates) { derivedStateOf {
        (if (dragging) target else state.firstVisibleItemIndex).coerceIn(0, dates.lastIndex)
    } }
    val day = dates[index]
    val label = remember(day, dateLabel) { dateLabel(day) }
    BoxWithConstraints(modifier.fillMaxHeight().padding(vertical = 12.dp)) {
        val travel = (maxHeight - 42.dp).coerceAtLeast(0.dp)
        Row(Modifier.align(Alignment.TopEnd).offset(y = travel * (index.toFloat() / dates.lastIndex.coerceAtLeast(1)))
            .height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(dragging || state.isScrollInProgress, enter = fadeIn(), exit = fadeOut()) {
                Surface(Modifier.padding(end = 8.dp), shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                }
            }
            Box(Modifier.size(28.dp, 42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center) { Text("⋮", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Box(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight().pointerInput(state, dates) {
            fun seek(y: Float) {
                val halfThumb = 21.dp.toPx()
                val fraction = ((y - halfThumb) / (size.height - halfThumb * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val next = (fraction * dates.lastIndex).toInt()
                if (target != next || state.firstVisibleItemIndex != next) {
                    target = next
                    state.requestScrollToItem(next)
                }
            }
            detectVerticalDragGestures(onDragStart = { dragging = true; seek(it.y) }, onDragEnd = { dragging = false },
                onDragCancel = { dragging = false }) { change, _ -> seek(change.position.y) }
        })
    }
}
