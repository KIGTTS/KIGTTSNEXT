package com.kigtts.next.ui.identity

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.isSectionRow
import com.kigtts.next.ui.components.rememberDragFeedback
import kotlin.math.abs

/** Track the pointer on the board rather than on the tile that's moving underneath it. */
@Composable
internal fun Modifier.profileTileMove(
    unit: Float,
    motions: Map<String, ProfileTileMotion>,
    tiles: () -> List<ProfileTile>,
    geometry: () -> ProfileBoardGeometry,
    change: (List<ProfileTile>) -> Unit,
): Modifier {
    val feedback by rememberUpdatedState(rememberDragFeedback())
    return pointerInput(unit) {
        val crossingGap = 14.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tile = tiles().asReversed().firstOrNull { item ->
                motions[item.id]?.bounds?.contains(down.position) == true
            } ?: return@awaitEachGesture
            val motion = motions.getValue(tile.id)
            val section = tile.kind.isSectionRow
            if (motion.gesture != null || motion.bounds.onActionHandle(down.position, section, 32.dp.toPx(), 32.dp.toPx()))
                return@awaitEachGesture
            // The clickable tile handles the down at Main; only later events can cancel the hold.
            awaitPointerEvent(PointerEventPass.Main)
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val anchor = held.position
            var current = tiles()
            val drop = ProfileTileDrop(current, tile.id, geometry())
            val start = motion.begin(TileGesture.MOVE)
            val dragFeedback = feedback
            dragFeedback.start(0f)
            var released = false
            var lastSwapY: Float? = null
            try {
                while (true) {
                    // Claim the pointer before a scrolling parent or the tile's click handler.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == held.id } ?: break
                    if (pointer.isConsumed) break
                    pointer.consume()
                    if (!pointer.pressed) {
                        released = true
                        break
                    }
                    val movement = pointer.position - anchor
                    val floating = start.translate(if (section) Offset(0f, movement.y) else movement)
                    motion.follow(floating)
                    val y = floating.center.y
                    if (section && lastSwapY?.let { abs(y - it) < crossingGap } == true) continue
                    val next = drop.at(floating)
                    if (next != current) {
                        current = next
                        if (section) lastSwapY = y
                        change(next)
                        dragFeedback.step()
                    }
                }
            } finally {
                dragFeedback.finish(cancelled = !released)
                motion.release()
            }
        }
    }
}

/** Keep the down in a corner available to the independent delete and resize controls. */
private fun Rect.onActionHandle(position: Offset, section: Boolean, deleteExtent: Float, resizeExtent: Float): Boolean {
    val delete = position.x < left + deleteExtent && (section || position.y < top + deleteExtent)
    val resize = !section && position.x >= right - resizeExtent && position.y >= bottom - resizeExtent
    return delete || resize
}
