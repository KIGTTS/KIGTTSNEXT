@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch
import java.io.File

/** Variable-width items preserve mixed image proportions without letterboxing each card. */
@Composable internal fun PluginGallery(block: PageBlock.Gallery, resolve: (PluginResource) -> File?) {
    val images = block.images.take(8)
    if (images.isEmpty()) return
    val state = rememberLazyListState()
    val current by remember { derivedStateOf { state.firstVisibleItemIndex } }
    val bitmaps = images.map { previewBitmap(it, resolve) }
    var expanded by remember(images) { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val targetHeight = (maxWidth * .6f).coerceIn(180.dp, 280.dp)
            val sizes = images.mapIndexed { index, image ->
                val ratio = if (image.width != null && image.height != null) image.width!!.toFloat() / image.height!!
                    else bitmaps[index]?.let { it.width.toFloat() / it.height } ?: 1f
                val width = (targetHeight * ratio).coerceAtMost((maxWidth * .88f).coerceAtMost(560.dp))
                width to width / ratio
            }
            LazyRow(state = state, modifier = Modifier.fillMaxWidth().height(sizes.maxOf { it.second })
                .testTag("plugin-preview-carousel"),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(end = (maxWidth - sizes.last().first).coerceAtLeast(0.dp)),
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start)) {
                itemsIndexed(images, key = { index, image -> "$index/${image.resource.path}" }) { index, original ->
                    val image = original.withPreviewLabel(index)
                    PreviewBitmap(image, bitmaps[index], Modifier.size(sizes[index].first, sizes[index].second)
                    .clickable(onClickLabel = "放大预览") { expanded = index }
                    .testTag("plugin-preview/$index"))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(images[current.coerceIn(images.indices)].description,
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (images.size > 1) Text("${current + 1} / ${images.size}",
                Modifier.testTag("plugin-preview-position"), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded in images.indices) PreviewGalleryViewer(images, expanded, resolve) { page ->
        scope.launch { state.scrollToItem(page); expanded = -1 }
    }
}

@Composable private fun PreviewGalleryViewer(images: List<PageImage>, initialPage: Int,
    resolve: (PluginResource) -> File?, dismiss: (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage) { images.size }
    Dialog(onDismissRequest = { dismiss(pager.currentPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Text("${pager.currentPage + 1} / ${images.size}", Modifier.weight(1f).testTag("plugin-preview-viewer-position"),
                    color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = { PlainTooltip(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface) { Text(uiText("action.close")) } }, state = rememberTooltipState()) {
                    MotionIconButton(onClick = { dismiss(pager.currentPage) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("plugin-preview-viewer"),
                key = { "$it/${images[it].resource.path}" }) { index ->
                val image = images[index].withPreviewLabel(index)
                PreviewBitmap(image, previewBitmap(image, resolve), Modifier.fillMaxSize())
            }
            Text(images[pager.currentPage].description, Modifier.fillMaxWidth().padding(top = 12.dp),
                color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun PageImage.withPreviewLabel(index: Int) =
    if (description.isBlank()) copy(description = uiText("plugin.preview.image_number", "number" to (index + 1).toString())) else this
