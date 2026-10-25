package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.cards.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

@Composable
fun CardPreviewScreen(cards: CardRepository, stickers: StickerRepository, cardId: String, openEditor: (String) -> Unit,
    message: (String) -> Unit, onQuickImage: (String, String) -> Unit = { _, _ -> }) {
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val readOnly by stickers.readOnlyCards.collectAsStateWithLifecycle()
    val frames by stickers.frameStyles.collectAsStateWithLifecycle()
    val saved = catalog.cards.firstOrNull { it.id == cardId }
    if (saved == null) { androidx.compose.material3.Text("这张拍立得已移除", Modifier.padding(24.dp)); return }
    var view by remember(cardId) { mutableStateOf(saved) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { view = saved }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(message); val quick = rememberUpdatedState(onQuickImage)
    val actions = remember(cards, stickers) { CardWorkspaceActions(context, cards, stickers, scope, openEditor,
        { callback.value(it) }, { id, title -> quick.value(id, title) }) }
    val active = LocalPageActive.current; val enabled = active && !busy && !actions.busy
    val face = com.kigtts.next.sharing.LocalFaceShare.current
    val receipt = com.kigtts.next.sharing.rememberFaceReceipt(cardId)
    var senderInfo by remember(cardId) { mutableStateOf(false) }
    RegisterPageLoading(busy || actions.busy)
    RegisterPageActions(buildList {
        if (cardId !in readOnly) add(ToolbarAction("edit-card-preview", "编辑拍立得", Icons.Default.Edit, enabled, click = { openEditor(cardId) }))
        add(ToolbarAction("share-card-preview", "分享拍立得", Icons.Default.Share, enabled, click = { actions.share(saved) }))
        if (face != null) add(ToolbarAction("face-card-preview", "面对面返图", Icons.Default.Devices, enabled, click = { face.send(listOf(cardId)) }))
        if (receipt != null) add(ToolbarAction("sender-card-preview", "发送者名片", Icons.Default.AccountCircle, enabled, click = { senderInfo = true }))
        add(ToolbarAction("quick-card-preview", "添加到快捷文本", Icons.Default.TextFields, enabled,
            click = { actions.cardAction(saved, CardMenuAction.QUICK) })) })
    fun finishPhoto() {
        if (busy) return
        val candidate = view
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(stickers) {
                    val current = requireNotNull(cards.cards.value.firstOrNull { it.id == cardId }) { "拍立得已移除" }
                    check(current.imageId == candidate.imageId) { "照片已更新，请重新调整" }
                    cards.update(current.copy(photoX = candidate.photoX, photoY = candidate.photoY, photoZoom = candidate.photoZoom))
                } }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { view = cards.cards.value.firstOrNull { it.id == cardId } ?: saved; message(failure.message ?: "未能保存照片位置") }
            finally { busy = false }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
        val frame = frames.firstOrNull { it.id == view.frameStyleId }
        val width = minOf(maxWidth, maxHeight * com.kigtts.next.cards.polaroidPaperGeometry(view, frame).aspectRatio.toFloat(), 520.dp).coerceAtLeast(1.dp)
        PolaroidCanvas(view, stickers::imageFile, Modifier.width(width), photoEditable = enabled && cardId !in readOnly,
            onPhotoChange = { view = it }, onPhotoEnd = ::finishPhoto,
            frame = frame)
    }
    if (active) CardQuickExportDialog(actions)
    if (active && senderInfo && receipt != null) com.kigtts.next.sharing.FaceReceiptDialog(receipt) { senderInfo = false }
}
