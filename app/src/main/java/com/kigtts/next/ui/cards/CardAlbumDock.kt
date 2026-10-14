@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File
import kotlinx.coroutines.launch

/** Allocate only detent sizes; the moving mask and chrome keep each drag in one gesture owner. */
@Composable
internal fun CardAlbumDock(albums: List<PolaroidAlbum>, selectedId: String, photos: List<PolaroidCard>,
    focusedId: String?, imageFile: (String) -> File?, enabled: Boolean, landscape: Boolean,
    expanded: Boolean, expand: (Boolean) -> Unit, chooseAlbum: (String) -> Unit, choosePhoto: (PolaroidCard) -> Unit,
    create: () -> Unit, rename: () -> Unit, remove: () -> Unit, modifier: Modifier = Modifier,
    manage: () -> Unit = {}, manageAlbum: (String) -> Unit = {}, sheet: AnchoredDraggableState<Int>? = null) {
    val albumScroll = rememberLazyListState(); val photoScroll = rememberLazyListState()
    val state = sheet ?: rememberCardAlbumSheet(if (expanded) 2 else 0)
    val scope = rememberCoroutineScope()
    if (sheet == null) LaunchedEffect(expanded) { state.animateTo(if (expanded) 2 else 0, tween(240)) }
    val stage = state.targetValue
    val railsEnabled = enabled && stage > 0
    LaunchedEffect(selectedId, albums, stage) {
        val target = albums.indexOfFirst { it.id == selectedId }
        if (stage == 2 && target >= 0) albumScroll.animateScrollToItem(target)
    }
    LaunchedEffect(selectedId, focusedId, photos, stage) {
        val target = photos.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
        if (expanded && photos.isNotEmpty()) photoScroll.animateScrollToItem(target)
    }
    val selected = albums.firstOrNull { it.id == selectedId }
    // Read the parent's available space before choosing the collapsed endpoint. Reading the
    // collapsed slot (54/60) here would incorrectly shrink the retained outgoing drawing layer.
    BoxWithConstraints(modifier) {
      val fullWidth = if (landscape) minOf(264.dp, maxWidth).coerceAtLeast(1.dp) else maxWidth
      val fullHeight = if (landscape) maxHeight else minOf(216.dp, maxHeight).coerceAtLeast(1.dp)
      val collapsedWidth = minOf(54.dp, fullWidth)
      val collapsedHeight = minOf(60.dp, fullHeight)
      val extents = if (landscape) listOf(collapsedWidth, minOf(144.dp, fullWidth), fullWidth)
          else listOf(collapsedHeight, minOf(160.dp, fullHeight), fullHeight)
      val density = LocalDensity.current
      val anchors = remember(extents, density) { DraggableAnchors { extents.forEachIndexed { index, size -> index at with(density) { size.toPx() } } } }
      SideEffect { if (state.anchors != anchors) state.updateAnchors(anchors, state.targetValue) }
      val visibleExtent = remember(state, anchors) { derivedStateOf {
          state.offset.takeIf(Float::isFinite) ?: anchors.positionOf(state.targetValue)
      } }
      val reveal = remember(state, anchors) { derivedStateOf {
          ((visibleExtent.value - anchors.positionOf(0)) / (anchors.positionOf(2) - anchors.positionOf(0)).coerceAtLeast(1f)).coerceIn(0f, 1f)
      } }
      val showPhotos by remember { derivedStateOf { state.targetValue > 0 || state.currentValue > 0 } }
      val showAlbums by remember { derivedStateOf { state.targetValue == 2 || state.currentValue == 2 } }
      val shape = homeCardShape()
      Box(Modifier.then(if (landscape) Modifier.width(extents[stage]).height(fullHeight)
          else Modifier.width(fullWidth).height(extents[stage])).testTag("card-album-dock")) {
      Box(Modifier.cardDockGeometry(fullWidth, fullHeight, landscape, reveal, shape)) {
       Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer, shape = shape) {
        val chrome: @Composable () -> Unit = {
            CardAlbumChrome(state, landscape, enabled, expanded, reveal, selected != null,
                selectedId != com.kigtts.next.cards.CardRepository.DEFAULT_ALBUM,
                toggle = { if (sheet == null) expand(!expanded) else scope.launch { state.animateTo(if (stage == 2) 0 else stage + 1, tween(240)) } },
                create = create, manage = manage, rename = rename, remove = remove)
        }
        val rails: @Composable (Modifier) -> Unit = { railModifier ->
            AnimatedVisibility(showPhotos, modifier = railModifier, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                CardAlbumRails(albums, selectedId, photos, focusedId, imageFile, railsEnabled, landscape,
                    albumScroll, photoScroll, chooseAlbum, choosePhoto,
                    temporarilyLocked = !com.kigtts.next.ui.navigation.LocalPageActive.current || enabled && !railsEnabled,
                    manageAlbum = manageAlbum, showAlbums = showAlbums)
            }
        }
        val contentModifier = Modifier.fillMaxSize().graphicsLayer {
            translationX = if (landscape) fullWidth.toPx() - visibleExtent.value else 0f
            translationY = if (landscape) 0f else fullHeight.toPx() - visibleExtent.value
        }.padding(6.dp)
        if (landscape) Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chrome()
            rails(Modifier.weight(1f).fillMaxHeight())
        } else Column(contentModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chrome()
            rails(Modifier.requiredHeight((fullHeight - 64.dp).coerceAtLeast(1.dp)).fillMaxWidth())
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
