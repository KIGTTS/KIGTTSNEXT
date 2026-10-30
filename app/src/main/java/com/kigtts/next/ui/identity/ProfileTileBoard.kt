package com.kigtts.next.ui.identity

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.rememberDragFeedback
import java.io.File
import kotlin.math.roundToInt

@Composable internal fun ProfileTileBoard(tiles: List<ProfileTile>, contacts: List<ContactLink>, image: (String) -> File?,
    modifier: Modifier = Modifier, editing: Boolean = false, change: (List<ProfileTile>) -> Unit = {},
    edit: (ProfileTile) -> Unit = {}, remove: (ProfileTile) -> Unit = {}, openContact: (ContactLink) -> Unit = {},
    openLink: (String) -> Unit = {}) {
    if (tiles.isEmpty()) return
    var preview by remember { mutableStateOf<Pair<File, String>?>(null) }
    val latestTiles by rememberUpdatedState(tiles)
    val latestChange by rememberUpdatedState(change)
    BoxWithConstraints(modifier.fillMaxWidth().testTag("profile-tiles")) {
        val density = LocalDensity.current
        val geometry = remember(tiles, maxWidth, density) { with(density) {
            profileBoardGeometry(tiles, maxWidth.toPx(), 12.dp.toPx(), 36.dp.toPx())
        } }
        val latestGeometry by rememberUpdatedState(geometry)
        val motions = remember { mutableMapOf<String, ProfileTileMotion>() }
        val ids = tiles.map { it.id }.toSet()
        SideEffect { motions.keys.retainAll(ids) }
        val boardHeight = animateFloatAsState(geometry.height, tween(220, easing = FastOutSlowInEasing), label = "tile-board-height")
        Box(Modifier.fillMaxWidth().layout { measurable, constraints ->
            val bottom = motions.values.maxOfOrNull { it.bounds.bottom } ?: 0f
            val height = constraints.constrainHeight(maxOf(boardHeight.value, bottom).roundToInt())
            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(placeable.width, height) { placeable.place(0, 0) }
        }) {
            tiles.forEach { tile -> key(tile.id) {
                val target = geometry.bounds.getValue(tile.id)
                val motion = remember { ProfileTileMotion(target) }
                val feedback by rememberUpdatedState(rememberDragFeedback())
                SideEffect { motions[tile.id] = motion }
                LaunchedEffect(target, motion.gesture) { motion.settle(target) }
                DisposableEffect(editing, geometry.unit) { onDispose { feedback.finish(cancelled = true); motion.release() } }
                val elevation by animateDpAsState(if (motion.raised) 8.dp else 0.dp, tween(140), label = "tile-lift")
                Box(Modifier.offset { motion.bounds.topLeft.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                    .layout { measurable, _ ->
                        val bounds = motion.bounds
                        val width = bounds.width.roundToInt().coerceAtLeast(1); val height = bounds.height.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    }.zIndex(if (motion.raised) 1f else 0f).testTag("profile-tile/${tile.id}")
                    .then(if (editing) Modifier.pointerInput(tile.id, geometry.unit) {
                        var start = Rect.Zero; var delta = Offset.Zero
                        detectDragGesturesAfterLongPress(onDragStart = { start = motion.begin(TileGesture.MOVE); delta = Offset.Zero; feedback.start(0f) },
                            onDragEnd = { feedback.finish(); motion.release() }, onDragCancel = { feedback.finish(cancelled = true); motion.release() }) { event, amount ->
                            event.consume(); delta += amount; val floating = start.translate(delta); motion.follow(floating)
                            val hovered = latestGeometry.bounds.entries.firstOrNull { it.value.contains(floating.center) }?.key
                            if (hovered != null && hovered != tile.id) {
                                val next = latestTiles.toMutableList(); val from = next.indexOfFirst { it.id == tile.id }; val to = next.indexOfFirst { it.id == hovered }
                                if (from >= 0 && to >= 0) { val item = next.removeAt(from); next.add(to.coerceAtMost(next.size), item); latestChange(next); feedback.step() }
                            }
                        }
                    } else Modifier)) {
                    val contact = tile.resolveContact(contacts)
                    val iconOnly = tile.columns == 1 && tile.rows == 1 && tile.kind in setOf(ProfileTileKind.LINK, ProfileTileKind.CONTACT)
                    val clickable = editing || tile.kind == ProfileTileKind.LINK || tile.kind == ProfileTileKind.IMAGE && tile.image.isNotBlank() || (tile.kind == ProfileTileKind.CONTACT && contact != null)
                    Surface(onClick = feedbackClick {
                        when {
                            editing -> edit(tile)
                            tile.kind == ProfileTileKind.IMAGE -> image(tile.image)?.let { preview = it to tile.title.ifBlank { "图片" } }
                            tile.kind == ProfileTileKind.LINK -> openLink(tile.text)
                            else -> contact?.let(openContact)
                        }
                    },
                        enabled = clickable, modifier = Modifier.fillMaxSize().then(if (iconOnly) Modifier.semantics {
                            contentDescription = tile.title.ifBlank { contact?.label ?: "链接" }
                        } else Modifier),
                        shape = if (tile.kind == ProfileTileKind.TITLE) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge,
                        color = if (tile.kind == ProfileTileKind.TITLE) MaterialTheme.colorScheme.surface.copy(alpha = 0f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = elevation) { ProfileTileContent(tile, contact, image, editing) }
                    if (editing) {
                        IconButton(feedbackClick { remove(tile) }, Modifier.align(Alignment.TopStart).then(if (tile.kind == ProfileTileKind.TITLE) Modifier else Modifier.offset((-6).dp, (-6).dp))
                            .size(32.dp).testTag("tile-remove/${tile.id}")) { Icon(Icons.Default.RemoveCircle, "移除磁贴", Modifier.size(22.dp)) }
                        if (tile.kind != ProfileTileKind.TITLE) ProfileTileResizeHandle(tile, motion, geometry, { columns, rows ->
                            latestChange(latestTiles.map { if (it.id == tile.id) it.copy(columns = columns, rows = rows) else it })
                        }, Modifier.align(Alignment.BottomEnd))
                    }
                }
            } }
        }
    }
    preview?.let { (file, description) -> ProfileImagePreview(file, description, { preview = null }) }
}
