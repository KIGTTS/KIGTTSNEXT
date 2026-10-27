package com.kigtts.next.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.ui.components.rememberDragFeedback
import kotlin.math.roundToInt

/** Pixel sizes follow the pointer; only crossed grid boundaries update the layout draft. */
@Composable internal fun ProfileTileResizeHandle(tile: ProfileTile, motion: ProfileTileMotion,
    geometry: ProfileBoardGeometry, change: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val currentTile by rememberUpdatedState(tile)
    val currentChange by rememberUpdatedState(change)
    val feedback by rememberUpdatedState(rememberDragFeedback())
    DisposableEffect(tile.id) { onDispose { feedback.finish(cancelled = true) } }
    Box(modifier.size(44.dp).testTag("tile-resize/${tile.id}")
        .semantics { contentDescription = "调整磁贴大小"; stateDescription = "${tile.columns} 列，${tile.rows} 行" }
        .pointerInput(tile.id, geometry.unit) {
            var start = Rect.Zero; var delta = Offset.Zero; var last = 0 to 0
            detectDragGestures(onDragStart = {
                start = motion.begin(TileGesture.RESIZE); delta = Offset.Zero
                last = currentTile.columns to currentTile.rows
                feedback.start(0f)
            }, onDragEnd = { feedback.finish(); motion.release() }, onDragCancel = { feedback.finish(cancelled = true); motion.release() }) { event, amount ->
                event.consume(); delta += amount
                val width = (start.width + delta.x).coerceIn(geometry.unit, geometry.width)
                val height = (start.height + delta.y).coerceIn(geometry.unit, geometry.unit * 3 + geometry.gap * 2)
                val left = start.left.coerceAtMost(geometry.width - width)
                motion.follow(Rect(Offset(left, start.top), Size(width, height)))
                val span = ((width + geometry.gap) / geometry.pitch).roundToInt().coerceIn(1, 4) to
                    ((height + geometry.gap) / geometry.pitch).roundToInt().coerceIn(1, 3)
                if (span != last) { last = span; currentChange(span.first, span.second); feedback.step() }
            }
        }, contentAlignment = Alignment.Center) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
        Canvas(Modifier.size(22.dp)) {
            drawPath(Path().apply { moveTo(4.dp.toPx(), size.height - 3.dp.toPx())
                quadraticTo(size.width - 3.dp.toPx(), size.height - 3.dp.toPx(), size.width - 3.dp.toPx(), 4.dp.toPx()) }, tint,
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
