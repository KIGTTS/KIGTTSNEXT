package com.kigtts.next.ui.identity

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.kigtts.next.identity.*
import java.io.File
import kotlin.math.roundToInt

@Composable internal fun ProfileTileBoard(tiles: List<ProfileTile>, contacts: List<ContactLink>, image: (String) -> File?,
    modifier: Modifier = Modifier, editing: Boolean = false, change: (List<ProfileTile>) -> Unit = {},
    edit: (ProfileTile) -> Unit = {}, remove: (ProfileTile) -> Unit = {}, openContact: (ContactLink) -> Unit = {},
    openLink: (String) -> Unit = {}) {
    if (tiles.isEmpty()) return
    val latestTiles by rememberUpdatedState(tiles)
    val latestChange by rememberUpdatedState(change)
    var moving by remember { mutableStateOf<String?>(null) }
    var resizing by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxWidth().testTag("profile-tiles")) {
        val unit = (maxWidth - 36.dp) / 4
        val density = LocalDensity.current
        val unitPx = with(density) { (unit + 12.dp).toPx() }
        val placements = packProfileTiles(tiles)
        val rowCount = placements.maxOf { it.row + it.tile.rows }
        val rowHeights = (0 until rowCount).map { row -> if (placements.any { it.row == row && it.tile.kind == ProfileTileKind.TITLE }) 36.dp else unit }
        val rowTops = rowHeights.runningFold(0.dp) { top, height -> top + height + 12.dp }
        val latestPlacements by rememberUpdatedState(placements)
        val latestTops by rememberUpdatedState(rowTops)
        Box(Modifier.fillMaxWidth().height(rowTops.last() - 12.dp)) {
            placements.forEach { place -> key(place.tile.id) {
                val tile = place.tile
                val target = with(density) { IntOffset(((unit + 12.dp) * place.column).roundToPx(), rowTops[place.row].roundToPx()) }
                val animated by animateIntOffsetAsState(target, tween(180), label = "tile-placement")
                val size = unit * tile.columns + 12.dp * (tile.columns - 1)
                val height = if (tile.kind == ProfileTileKind.TITLE) 36.dp else unit * tile.rows + 12.dp * (tile.rows - 1)
                val widthAnimated by animateDpAsState(size, if (resizing == tile.id) snap() else tween(180), label = "tile-width")
                val heightAnimated by animateDpAsState(height, if (resizing == tile.id) snap() else tween(180), label = "tile-height")
                val currentTile by rememberUpdatedState(tile)
                Box(Modifier.offset { if (moving == tile.id) IntOffset((origin.x + drag.x).roundToInt(), (origin.y + drag.y).roundToInt()) else animated }
                    .size(widthAnimated, heightAnimated).zIndex(if (moving == tile.id || resizing == tile.id) 1f else 0f)
                    .testTag("profile-tile/${tile.id}")
                    .then(if (editing) Modifier.pointerInput(tile.id, editing, unitPx) {
                        detectDragGesturesAfterLongPress(onDragStart = {
                            val position = latestPlacements.first { it.tile.id == tile.id }
                            moving = tile.id; origin = Offset(position.column * unitPx, latestTops[position.row].toPx()); drag = Offset.Zero
                        }, onDragEnd = { moving = null; drag = Offset.Zero }, onDragCancel = { moving = null; drag = Offset.Zero }) { event, delta ->
                            event.consume(); drag += delta
                            val center = origin + drag + Offset((currentTile.columns * unitPx - 12.dp.toPx()) / 2,
                                (if (currentTile.kind == ProfileTileKind.TITLE) 36.dp.toPx() else currentTile.rows * unitPx - 12.dp.toPx()) / 2)
                            val hovered = latestPlacements.firstOrNull { p ->
                                val left = p.column * unitPx; val top = latestTops[p.row].toPx()
                                center.x in left..(left + p.tile.columns * unitPx - 12.dp.toPx()) &&
                                    center.y in top..(top + if (p.tile.kind == ProfileTileKind.TITLE) 36.dp.toPx() else p.tile.rows * unitPx - 12.dp.toPx())
                            }?.tile?.id
                            if (hovered != null && hovered != tile.id) {
                                val next = latestTiles.toMutableList(); val from = next.indexOfFirst { it.id == tile.id }; val to = next.indexOfFirst { it.id == hovered }
                                if (from >= 0 && to >= 0) { val item = next.removeAt(from); next.add(to.coerceAtMost(next.size), item); latestChange(next) }
                            }
                        }
                    } else Modifier)) {
                    val link = contacts.firstOrNull { it.value == tile.contact }
                    val clickable = editing || tile.kind == ProfileTileKind.LINK || (tile.kind == ProfileTileKind.CONTACT && link != null)
                    val onClick = { if (editing) edit(tile) else if (tile.kind == ProfileTileKind.LINK) openLink(tile.text) else link?.let(openContact); Unit }
                    Surface(onClick = onClick, enabled = clickable, modifier = Modifier.fillMaxSize(),
                        shape = if (tile.kind == ProfileTileKind.TITLE) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge,
                        color = if (tile.kind == ProfileTileKind.TITLE) MaterialTheme.colorScheme.surface.copy(alpha = 0f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = if (moving == tile.id) 8.dp else 0.dp) {
                        if (tile.kind == ProfileTileKind.IMAGE && tile.image.isNotBlank()) ProfileTileImage(image(tile.image), Modifier.fillMaxSize(), tile.title.ifBlank { "图片" })
                        else if (tile.kind == ProfileTileKind.TITLE) Text(tile.title, Modifier.padding(start = if (editing) 36.dp else 4.dp, top = 4.dp, end = 4.dp),
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else if (tile.rows == 1) Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tile.kind == ProfileTileKind.CONTACT) SocialContactIcon(link ?: ContactLink("", ""), Modifier.size(24.dp))
                            if (tile.kind == ProfileTileKind.LINK) Icon(Icons.Default.Link, null, Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tile.title.ifBlank { link?.label.orEmpty() }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (tile.kind == ProfileTileKind.CONTACT) link?.value ?: "联系方式已移除" else tile.text,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                            if (tile.kind == ProfileTileKind.CONTACT) SocialContactIcon(link ?: ContactLink("", ""), Modifier.size(28.dp))
                            if (tile.kind == ProfileTileKind.LINK) Icon(Icons.Default.Link, null, Modifier.size(24.dp))
                            Text(tile.title.ifBlank { link?.label ?: if (tile.kind == ProfileTileKind.STATUS) "状态" else "" },
                                style = if (tile.kind == ProfileTileKind.TITLE) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                                maxLines = if (tile.kind == ProfileTileKind.TITLE) 1 else 2, overflow = TextOverflow.Ellipsis)
                            if (tile.kind != ProfileTileKind.TITLE) Text(if (tile.kind == ProfileTileKind.CONTACT) link?.value ?: "联系方式已移除" else tile.text,
                                style = MaterialTheme.typography.bodyMedium, maxLines = tile.rows * 2, color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (editing) {
                        IconButton({ remove(tile) }, Modifier.align(Alignment.TopStart).then(if (tile.kind == ProfileTileKind.TITLE) Modifier else Modifier.offset((-6).dp, (-6).dp))
                            .size(32.dp).testTag("tile-remove/${tile.id}")) { Icon(Icons.Default.RemoveCircle, "移除磁贴", Modifier.size(22.dp)) }
                        if (tile.kind != ProfileTileKind.TITLE) {
                            var total by remember { mutableStateOf(Offset.Zero) }
                            var start by remember { mutableStateOf(2 to 1) }
                            Box(Modifier.align(Alignment.BottomEnd).size(44.dp).testTag("tile-resize/${tile.id}")
                                .semantics { contentDescription = "调整磁贴大小"; stateDescription = "${tile.columns} 列，${tile.rows} 行" }
                                .pointerInput(tile.id, unitPx) { detectDragGestures(onDragStart = {
                                    resizing = tile.id; start = currentTile.columns to currentTile.rows; total = Offset.Zero
                                }, onDragEnd = { resizing = null }, onDragCancel = { resizing = null }) { event, delta ->
                                    event.consume(); total += delta
                                    val columns = (start.first + (total.x / unitPx).roundToInt()).coerceIn(1, 4)
                                    val rows = (start.second + (total.y / unitPx).roundToInt()).coerceIn(1, 3)
                                    latestChange(latestTiles.map { if (it.id == tile.id) it.copy(columns = columns, rows = rows) else it })
                                } }, contentAlignment = Alignment.Center) {
                                val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
                                Canvas(Modifier.size(22.dp)) { val canvasSize = this.size
                                    drawPath(Path().apply { moveTo(4.dp.toPx(), canvasSize.height - 3.dp.toPx());
                                    quadraticTo(canvasSize.width - 3.dp.toPx(), canvasSize.height - 3.dp.toPx(), canvasSize.width - 3.dp.toPx(), 4.dp.toPx()) }, tint,
                                    style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)) }
                            }
                        }
                    }
                }
            } }
        }
    }
}
