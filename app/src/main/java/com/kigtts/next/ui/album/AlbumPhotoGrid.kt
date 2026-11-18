package com.kigtts.next.ui.album

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.kigtts.next.media.AlbumMedia
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.components.feedbackClick
import java.time.LocalDate

private data class PhotoRow(val day: LocalDate, val photo: AlbumMedia? = null)

@Composable
internal fun AlbumPhotoGrid(photos: List<AlbumMedia>, grid: LazyGridState, loading: Boolean,
    zoom: Int, changeZoom: (Int) -> Unit, openCollections: () -> Unit, selecting: Boolean, selected: Set<String>,
    tapPhoto: (AlbumMedia) -> Unit, holdPhoto: ((AlbumMedia) -> Unit)?, modifier: Modifier = Modifier) {
    val rows = remember(photos) { buildList {
        var previous: LocalDate? = null
        photos.forEach { photo ->
            val day = photo.day
            if (day != previous) { previous = day; add(PhotoRow(day)) }
            add(PhotoRow(day, photo))
        }
    } }
    val dates = remember(rows) { rows.map { it.day } }
    val dateLabel = rememberAlbumDateFormat()
    BoxWithConstraints(modifier) {
        val normal = ((maxWidth - 8.dp) / 100.dp).toInt().coerceAtLeast(2)
        val minimum = ((maxWidth - 8.dp) / 235.dp).toInt().coerceAtLeast(1)
        val maximum = ((maxWidth - 8.dp) / 54.dp).toInt().coerceAtLeast(normal + 1)
        val columns = (normal + zoom).coerceIn(minimum, maximum)
        val density = LocalDensity.current
        val reflow = rememberAlbumGridReflow(grid)
        val step by rememberUpdatedState<(Boolean, Offset) -> Boolean> { shrink, center ->
            val target = columns + if (shrink) 1 else -1
            if (shrink && target >= maximum && photos.isNotEmpty()) {
                openCollections()
                true
            } else {
                if (target >= minimum && photos.isNotEmpty()) {
                    val layout = grid.layoutInfo
                    val anchor = layout.visibleItemsInfo.nearestAlbumItem(center) { rows.getOrNull(it.index)?.photo != null }
                    reflow.begin()
                    anchor?.let {
                        val gap = with(density) { 3.dp.toPx() }
                        val newSize = (it.size.height + gap) * columns / target - gap
                        val fraction = ((center.y - it.offset.y) / it.size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        grid.requestScrollToItem(it.index, (-it.offset.y + (newSize - it.size.height) * fraction).toInt())
                    }
                    changeZoom(target - normal)
                }
                false
            }
        }
        Box(Modifier.fillMaxSize().clipToBounds().albumGridZoom { shrink, center -> step(shrink, center) }) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("album-photos"), state = grid,
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(rows.size, key = { i -> rows[i].photo?.uri?.toString() ?: "day/${rows[i].day}" },
                    span = { i -> GridItemSpan(if (rows[i].photo == null) maxLineSpan else 1) }) { i ->
                    val row = rows[i]
                    val media = row.photo
                    if (media == null) Text(remember(row.day, dateLabel) { dateLabel(row.day) },
                        Modifier.albumReflow(reflow, "day/${row.day}", resize = false).padding(start = 12.dp, top = 20.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.titleMedium)
                    else {
                        val checked = media.uri.toString() in selected
                        Box(Modifier.aspectRatio(1f).albumReflow(reflow, media.uri.toString()).clip(RoundedCornerShape(3.dp)).combinedClickable(
                            onClick = feedbackClick { tapPhoto(media) },
                            onLongClick = holdPhoto?.let { feedbackClick { it(media) } })) {
                            AlbumImage(media, Modifier.fillMaxSize())
                            if (media.video) Text("▶ ${media.duration / 60000}:${(media.duration / 1000 % 60).toString().padStart(2, '0')}",
                                Modifier.align(Alignment.BottomEnd).padding(6.dp), color = Color.White)
                            if (selecting) Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                uiText(if (checked) "accessibility.selected" else "action.select"), Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp),
                                tint = if (checked) MaterialTheme.colorScheme.primary else Color.White)
                        }
                    }
                }
            }
            AlbumTimeline(grid, dates, dateLabel, Modifier.align(Alignment.CenterEnd))
            EmptyContent(!loading && photos.isEmpty(), Icons.Default.PhotoLibrary, uiText("photo.empty"), description = uiText("photo.empty_hint"))
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
