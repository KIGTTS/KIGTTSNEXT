package com.kigtts.next.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

@Composable
internal fun StickerLibraryPanel(libraries: List<StickerLibrary>, items: List<StickerItem>, selectedLibrary: String?,
    selectLibrary: (String) -> Unit, imageFile: (String) -> File?, enabled: Boolean,
    addLibrary: () -> Unit, editLibrary: (StickerLibrary) -> Unit,
    selectItem: (StickerItem) -> Unit, itemMenu: (StickerItem) -> Unit, modifier: Modifier = Modifier, showLibraryActions: Boolean = true) {
    val selected = libraries.firstOrNull { it.id == selectedLibrary } ?: libraries.firstOrNull()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                libraries.forEach { library -> FilterChip(selected?.id == library.id, feedbackClick { selectLibrary(library.id) },
                    label = { Text(library.name, maxLines = 1) }, enabled = enabled,
                    modifier = Modifier.testTag("sticker-library/" + library.id)) }
            }
            if (showLibraryActions) MotionIconButton(feedbackClick { selected?.let(editLibrary) }, enabled = enabled && selected != null) {
                Icon(Icons.Default.Edit, "编辑贴纸库")
            }
            if (showLibraryActions) MotionIconButton(feedbackClick(addLibrary), enabled = enabled) { Icon(Icons.Default.Add, "新建贴纸库") }
        }
        val contents = items.filter { it.libraryId == selected?.id }
        if (contents.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Collections, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("把喜欢的表情和图片放在这里", style = MaterialTheme.typography.bodyLarge)
                Text("使用上方的拍照或选图按钮添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.weight(1f).fillMaxWidth().testTag("sticker-grid"),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(contents, key = { it.id }) { item ->
                val draggable = LocalCardElementDrag.current != null
                Surface(Modifier.animateItem().cardElementDragSource("sticker/" + item.id, CardLayer(kind = CardLayerKind.IMAGE, imageId = item.imageId, width = .32))
                    .combinedClickable(enabled = enabled,
                    onClick = feedbackClick { selectItem(item) }, onLongClick = if (draggable) null else feedbackClick { itemMenu(item) })
                    .testTag("sticker-item/" + item.id), color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(10.dp)) {
                        val image = rememberCardImage(imageFile(item.imageId))
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            if (image != null) Image(image, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            else Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            MotionIconButton(feedbackClick { itemMenu(item) }, Modifier.size(36.dp), enabled) {
                                Icon(Icons.Default.MoreVert, "贴纸操作：" + item.name, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
