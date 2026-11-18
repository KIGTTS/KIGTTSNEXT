package com.kigtts.next.ui.album

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

/** Title picker: rectangular rows only; pinch gestures are reserved for the full gallery. */
@Composable
internal fun AlbumDropdownList(albums: List<AlbumCollection>, selectedId: String, modifier: Modifier = Modifier,
    onSelect: (AlbumCollection) -> Unit) {
    BoxWithConstraints(modifier.testTag("album-dropdown-list")) {
        LazyVerticalGrid(GridCells.Fixed(if (maxWidth >= 600.dp) 2 else 1), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(albums, key = { it.id }) { album ->
                val active = album.id == selectedId
                val scheme = MaterialTheme.colorScheme
                Surface(onClick = feedbackClick { onSelect(album) },
                    modifier = Modifier.fillMaxWidth().testTag("album-dropdown/${album.id}"),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (active) scheme.secondaryContainer else scheme.surfaceContainerHigh).copy(alpha = .83f),
                    contentColor = if (active) scheme.onSecondaryContainer else scheme.onSurface) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(scheme.surfaceVariant).testTag("album-dropdown-cover/${album.id}"),
                            contentAlignment = Alignment.Center) {
                            album.media.firstOrNull()?.let { AlbumImage(it, Modifier.fillMaxSize()) }
                                ?: Icon(Icons.Default.PhotoLibrary, null)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(album.name, Modifier.testTag("album-dropdown-name/${album.id}"), style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(uiText("album.item_count", "count" to album.media.size.toString()), style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant)
                        }
                        if (active) Icon(Icons.Default.Check, "当前相册")
                    }
                }
            }
        }
    }
}
