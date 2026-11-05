package com.kigtts.next.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.AlbumMedia
import com.kigtts.next.ui.components.feedbackClick
import java.text.Collator

internal data class AlbumCollection(val id: String, val name: String, val media: List<AlbumMedia>)

internal fun albumCollections(media: List<AlbumMedia>, allLabel: String): List<AlbumCollection> {
    val collator = Collator.getInstance()
    val folders = media.groupBy { it.bucketId }.map { (id, items) ->
        AlbumCollection("folder/$id", items.first().bucketName.ifBlank { "其他" }, items)
    }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    return listOf(AlbumCollection("all", allLabel, media)) + folders
}

@Composable
internal fun AlbumTitle(album: AlbumCollection, albums: List<AlbumCollection>, onSelect: (AlbumCollection) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "切换相册",
            onClick = feedbackClick { expanded = true }).padding(vertical = 8.dp).testTag("album-title"),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(album.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 420.dp)) {
            albums.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = feedbackClick { expanded = false; onSelect(option) },
                    trailingIcon = if (option.id == album.id) ({ Icon(Icons.Default.Check, "当前相册") }) else null,
                )
            }
        }
    }
}

@Composable
internal fun AlbumCollectionGrid(albums: List<AlbumCollection>, onPinchOpen: (AlbumCollection) -> Unit,
    onSelect: (AlbumCollection) -> Unit) {
    val state = rememberLazyGridState()
    val pinch by rememberUpdatedState<(Boolean, androidx.compose.ui.geometry.Offset) -> Boolean> { shrink, center ->
        val item = if (!shrink) state.layoutInfo.visibleItemsInfo.nearestAlbumItem(center) else null
        item?.let { albums.getOrNull(it.index) }?.let(onPinchOpen)
        item != null
    }
    LazyVerticalGrid(GridCells.Adaptive(145.dp), Modifier.fillMaxSize().testTag("album-collections")
        .albumGridZoom { shrink, center -> pinch(shrink, center) }, state = state,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { album ->
            Surface(onClick = feedbackClick { onSelect(album) }, shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    Box(Modifier.aspectRatio(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        val cover = album.media.firstOrNull()
                        if (cover != null) AlbumImage(cover, Modifier.fillMaxSize())
                        else Icon(Icons.Default.PhotoLibrary, null)
                    }
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.media.size} 项", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
