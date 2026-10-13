@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

/** The parent sees only two sizes; fade/slide updates drawing layers rather than the browser's constraints. */
@Composable
internal fun CardAlbumDock(albums: List<PolaroidAlbum>, selectedId: String, photos: List<PolaroidCard>,
    focusedId: String?, imageFile: (String) -> File?, enabled: Boolean, landscape: Boolean,
    expanded: Boolean, expand: (Boolean) -> Unit, chooseAlbum: (String) -> Unit, choosePhoto: (PolaroidCard) -> Unit,
    create: () -> Unit, rename: () -> Unit, remove: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember(selectedId) { mutableStateOf(false) }
    val albumScroll = rememberLazyListState(); val photoScroll = rememberLazyListState()
    val reveal = animateFloatAsState(if (expanded) 1f else 0f, tween(200), label = "card-album-reveal")
    val railsEnabled by remember(enabled, expanded) { derivedStateOf { enabled && expanded && reveal.value >= .999f } }
    LaunchedEffect(selectedId, albums, expanded) {
        val target = albums.indexOfFirst { it.id == selectedId }
        if (expanded && target >= 0) albumScroll.animateScrollToItem(target)
    }
    LaunchedEffect(selectedId, focusedId, photos, expanded) {
        val target = photos.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
        if (expanded && photos.isNotEmpty()) photoScroll.animateScrollToItem(target)
    }
    val selected = albums.firstOrNull { it.id == selectedId }
    // Read the parent's available space before choosing the collapsed endpoint. Reading the
    // collapsed slot (54/60) here would incorrectly shrink the retained outgoing drawing layer.
    BoxWithConstraints(modifier) {
      val fullWidth = if (landscape) minOf(224.dp, maxWidth).coerceAtLeast(1.dp) else maxWidth
      val fullHeight = if (landscape) maxHeight else minOf(208.dp, maxHeight).coerceAtLeast(1.dp)
      val collapsedWidth = minOf(54.dp, fullWidth)
      val collapsedHeight = minOf(60.dp, fullHeight)
      Box(Modifier.then(if (landscape) Modifier.width(if (expanded) fullWidth else collapsedWidth).height(fullHeight)
          else Modifier.width(fullWidth).height(if (expanded) fullHeight else collapsedHeight)).testTag("card-album-dock")) {
      Box(Modifier.cardDockGeometry(fullWidth, fullHeight, landscape, reveal, MaterialTheme.shapes.large)) {
       Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp).zIndex(1f).graphicsLayer {
                translationX = if (landscape) (fullWidth - collapsedWidth).toPx() * (1f - reveal.value) else 0f
                translationY = if (landscape) 0f else (fullHeight - collapsedHeight).toPx() * (1f - reveal.value)
            }.background(MaterialTheme.colorScheme.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically) {
                ToolButton(if (expanded) "收起相册" else "展开相册",
                    if (landscape) { if (expanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft }
                    else { if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp },
                    { expand(!expanded) }, Modifier.testTag("toggle-card-albums"), size = 40.dp,
                    shapes = IconButtonDefaults.shapes(RoundedCornerShape(20.dp), RoundedCornerShape(10.dp)), enabled = enabled)
                Text(selected?.name ?: "相册", Modifier.weight(1f).padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = if (landscape) reveal.value else 1f },
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                ToolButton("新建相册", Icons.Default.CreateNewFolder, create,
                    Modifier.testTag("create-card-album").graphicsLayer { alpha = if (landscape) reveal.value else 1f },
                    size = 36.dp, enabled = enabled && (!landscape || expanded))
                Box {
                    ToolButton("相册操作", Icons.Default.MoreVert, { menu = true },
                        Modifier.graphicsLayer { alpha = if (landscape) reveal.value else 1f }, size = 36.dp,
                        enabled = enabled && selected != null && (!landscape || expanded))
                    DropdownMenu(menu && enabled, { menu = false }) {
                        DropdownMenuItem({ Text("重命名") }, feedbackClick { menu = false; rename() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) })
                        if (selectedId != com.kigtts.next.cards.CardRepository.DEFAULT_ALBUM)
                            DropdownMenuItem({ Text("删除相册") }, feedbackClick { menu = false; remove() },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                    }
                }
            }
            AnimatedVisibility(expanded, modifier = Modifier
                .then(if (landscape) Modifier.requiredWidth((fullWidth - 12.dp).coerceAtLeast(1.dp)).weight(1f)
                    else Modifier.requiredHeight((fullHeight - 64.dp).coerceAtLeast(1.dp)).fillMaxWidth())
                .graphicsLayer {
                    translationX = if (landscape) (1f - reveal.value) * 20.dp.toPx() else 0f
                    translationY = if (landscape) 0f else (1f - reveal.value) * 18.dp.toPx()
                }, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                CardAlbumRails(albums, selectedId, photos, focusedId, imageFile, railsEnabled, landscape,
                    albumScroll, photoScroll, chooseAlbum, choosePhoto, temporarilyLocked = enabled && !railsEnabled)
            }
        }
       }
      }
      }
    }
}

@Composable
internal fun CardAlbumTargetDialog(albums: List<PolaroidAlbum>, currentId: String?, dismiss: () -> Unit, choose: (String) -> Unit) {
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text("移到相册") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                albums.forEach { album ->
                    ListItem(headlineContent = { Text(album.name) }, leadingContent = { Icon(Icons.Default.PhotoAlbum, null) },
                        trailingContent = { if (album.id == currentId) Icon(Icons.Default.Check, "当前相册") },
                        modifier = Modifier.clickable(enabled = album.id != currentId, onClick = dialogFeedbackClick { choose(album.id) })
                            .testTag("move-card-album/" + album.id))
                }
            }
        }, confirmButton = { MotionTextButton(dialogFeedbackClick(dismiss)) { Text("取消") } })
}
