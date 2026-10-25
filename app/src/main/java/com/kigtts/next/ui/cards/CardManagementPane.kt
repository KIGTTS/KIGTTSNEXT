@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.*

/** Native selection and confirmed batch operations share the same catalog transaction path as plugins. */
@Composable internal fun CardManagementPane(state: CardManagementState, cards: CardRepository, stickers: StickerRepository,
    catalog: StickerSnapshot, frames: List<PolaroidFrameStyle>, actions: CardWorkspaceActions,
    enabled: Boolean, columns: Int, moved: (List<String>, String) -> Unit, edit: (String) -> Unit) {
    val albumsMode = state.displayMode == CardManagementMode.ALBUMS
    val album = catalog.albums.firstOrNull { it.id == state.albumId }
    val photoIds = album?.cardIds.orEmpty()
    val keys = if (albumsMode) catalog.albums.map { it.id } else photoIds
    val selected = keys.filter { it in state.selected }
    val faceShare = com.kigtts.next.sharing.LocalFaceShare.current
    val removable = if (albumsMode) selected.filterNot { it == CardRepository.DEFAULT_ALBUM } else selected
    var delete by remember { mutableStateOf<List<String>?>(null) }
    var move by remember { mutableStateOf<List<String>?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<List<String>, PolaroidAlbum>?>(null) }
    var rename by remember { mutableStateOf<PolaroidAlbum?>(null) }
    LaunchedEffect(keys) { state.reconcile(keys) }
    val interactive = enabled && state.active && LocalCardManagementActive.current && LocalCardManagementReveal.current.value >= .999f
    if (state.active) RegisterPageActions(buildList {
        add(ToolbarAction("cards-manage-done", "完成整理", Icons.Default.Check, enabled, click = state::finish))
        add(ToolbarAction("cards-manage-select-all", if (selected.size == keys.size && keys.isNotEmpty()) "取消全选" else "全选",
            Icons.Default.SelectAll, interactive && keys.isNotEmpty(), click = { state.selectAll(keys) }))
        if (!albumsMode) {
            if (faceShare != null) add(ToolbarAction("cards-manage-face-share", "面对面返图", Icons.Default.Devices,
                interactive && selected.isNotEmpty(), click = { faceShare.send(selected) }))
            add(ToolbarAction("cards-manage-move", "移动所选照片", Icons.Default.DriveFileMove, interactive && selected.isNotEmpty(), click = { move = selected }))
            if (selected.size == 1 && !cards.isReadOnly(selected.single())) add(ToolbarAction("cards-manage-edit", "编辑照片", Icons.Default.Edit, interactive, click = { state.finish(); edit(selected.single()) }))
        } else if (selected.size == 1) {
            add(ToolbarAction("cards-manage-rename", "重命名相册", Icons.Default.Edit, interactive,
                click = { rename = catalog.albums.firstOrNull { it.id == selected.single() } }))
        }
        add(ToolbarAction("cards-manage-delete", if (albumsMode) "删除所选相册" else "删除所选照片",
            Icons.Default.DeleteOutline, interactive && removable.isNotEmpty(), click = { delete = removable }))
    })
    val topInset = LocalCardHeaderClearance.current.coerceAtLeast(0.dp)
    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("card-management")) {
        Text("已选择 ${selected.size} " + if (albumsMode) "个相册" else "张照片", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("card-management-count"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CardManagementSortPane(keys, state.displayMode, columns, interactive, actions.busy, state.originId,
                order = { ids -> actions.work({ if (albumsMode) cards.orderAlbums(ids) else cards.orderCards(requireNotNull(state.albumId), ids) }) }) { id, modifier, grip, lifted ->
                if (albumsMode) catalog.albums.firstOrNull { it.id == id }?.let {
                    CardManagementAlbum(it, id in state.selected, interactive, { state.toggle(id) }, modifier, grip, lifted)
                } else catalog.cards.firstOrNull { it.id == id }?.let {
                    CardManagementPhoto(it, id in state.selected, interactive, stickers::imageFile,
                        frames.firstOrNull { frame -> frame.id == it.frameStyleId }, { state.toggle(id) }, modifier, grip, lifted, state.originId)
                }
            }
        }
    }
    if (state.active) {
        delete?.let { ids -> CardDeleteDialog(if (albumsMode) "删除 ${ids.size} 个相册？" else "删除 ${ids.size} 张照片？",
            if (albumsMode) "照片将移到默认相册，默认相册会保留。" else "所选照片将从相册中删除。",
            { delete = null }, {
                delete = null
                actions.work({ if (albumsMode) cards.removeAlbums(ids) else cards.removeCards(ids) }) { state.clear() }
            }) }
        move?.let { ids -> CardAlbumTargetDialog(catalog.albums, state.albumId, { move = null }) { target ->
            move = null; catalog.albums.firstOrNull { it.id == target }?.let { moveTarget = ids to it }
        } }
        moveTarget?.let { (ids, target) -> MotionAlertDialog(onDismissRequest = { moveTarget = null },
            title = { Text("移动 ${ids.size} 张照片？") }, text = { Text("移到「${target.name}」") },
            confirmButton = { MotionTextButton(dialogFeedbackClick {
                moveTarget = null; actions.work({ cards.moveCards(ids, target.id) }) { state.finish(); moved(ids, target.id) }
            }, Modifier.testTag("confirm-move-cards")) { Text("移动") } },
            dismissButton = { MotionTextButton(dialogFeedbackClick { moveTarget = null }) { Text("取消") } }) }
        rename?.let { target -> CardNameDialog("重命名相册", target.name, { rename = null }, confirm = { name ->
            rename = null; actions.work({ cards.renameAlbum(target.id, name) })
        }) }
    }
}
