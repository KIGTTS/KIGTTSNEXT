package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.cards.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.PolaroidCard

@Composable
fun CardsScreen(cards: CardRepository, stickers: StickerRepository, openPreview: (String) -> Unit,
    openEditor: (String) -> Unit, message: (String) -> Unit, onQuickImage: (String, String) -> Unit = { _, _ -> },
    openCamera: () -> Unit = {}) {
    val context = LocalContext.current
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val frames by stickers.frameStyles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val editor = rememberUpdatedState(openEditor); val feedback = rememberUpdatedState(message); val quick = rememberUpdatedState(onQuickImage)
    val actions = remember(cards, stickers) { CardWorkspaceActions(context, cards, stickers, scope,
        { editor.value(it) }, { feedback.value(it) }, { id, title -> quick.value(id, title) }) }
    var removeCard by remember { mutableStateOf<PolaroidCard?>(null) }
    val preferences = remember(context) { context.getSharedPreferences("cards-view", 0) }
    var discovery by remember { mutableStateOf(preferences.getBoolean("face-discovery", false)) }
    val active = LocalPageActive.current
    LaunchedEffect(cards) {
        try { cards.migrateLegacy() } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { message("无法读取旧拍立得，请重试") }
    }
    val photo = rememberCardPhotoSources({ actions.importPhoto(it) }, message)
    val enabled = active && !actions.busy && !photo.processing
    RegisterPageLoading(actions.busy || photo.processing)
    RegisterPageActions(listOf(ToolbarAction("face-discovery", "面对面发现", Icons.Default.Radar, active,
        checked = discovery, click = {
            discovery = !discovery; preferences.edit().putBoolean("face-discovery", discovery).apply()
            message(if (discovery) "已预留面对面发现，暂不搜索附近设备" else "已关闭面对面发现")
        })))
    Box(Modifier.fillMaxSize()) {
        PolaroidCardsGrid(catalog.cards, stickers::imageFile, enabled, { openPreview(it.id) }, { openEditor(it.id) },
            actions::share, { card, operation ->
                if (operation == CardMenuAction.DELETE) removeCard = card else actions.cardAction(card, operation)
            }, frames = frames)
        CardAddFab(enabled, openCamera, photo.gallery, actions::newCard)
    }
    if (active) removeCard?.let { card -> CardDeleteDialog("删除这张拍立得？", card.title, { removeCard = null }, {
        removeCard = null; actions.cardAction(card, CardMenuAction.DELETE)
    }) }
}
