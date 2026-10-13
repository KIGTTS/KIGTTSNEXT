package com.kigtts.next.ui.cards

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.plugin.api.PolaroidCard

/** This owner outlives the outgoing camera scene, so its native preview window never gets replaced. */
@Composable
fun PolaroidPrintPreviewHost(model: AppViewModel) {
    val handoff by model.cardCapture.printPreviewHandoff.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val catalog by model.stickers.snapshot.collectAsStateWithLifecycle()
    val frames by model.stickers.frameStyles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember(model, context, scope) { CardWorkspaceActions(context, model.cards, model.stickers, scope,
        { model.navigate(Destination.CardEditor(it)) }, model::message, model::quickImageCard) }
    var removeCard by remember { mutableStateOf<PolaroidCard?>(null) }
    var moveCard by remember { mutableStateOf<PolaroidCard?>(null) }
    LaunchedEffect(handoff?.requestId, catalog.cards) {
        handoff?.takeIf { preview -> catalog.cards.none { it.id == preview.card.id } }?.let {
            model.cardCapture.abortPrintPreview(it.requestId)
        }
        removeCard = removeCard?.let { candidate -> catalog.cards.firstOrNull { it.id == candidate.id } }
        moveCard = moveCard?.let { candidate -> catalog.cards.firstOrNull { it.id == candidate.id } }
    }
    handoff?.let { preview ->
        val source = state.destination as? Destination.CardCamera
        val foreground = source?.let { it.requestId == preview.requestId && it.cardId == null } == true
        if (preview.shown || foreground) key(preview.requestId) {
            val card = catalog.cards.firstOrNull { it.id == preview.card.id } ?: preview.card
            fun dismiss() = model.cardCapture.dismissPrintPreview(preview.requestId)
            CardPreviewDialog(card, model.stickers::imageFile, frames.firstOrNull { it.id == card.frameStyleId },
                ::dismiss, { dismiss(); model.navigate(Destination.CardEditor(card.id)) }, { actions.share(card) },
                { dismiss(); actions.cardAction(card, CardMenuAction.QUICK) }, { operation ->
                    if (operation == CardMenuAction.DELETE) { dismiss(); removeCard = card }
                    else actions.cardAction(card, operation)
                }, { dismiss(); moveCard = card }, enabled = !actions.busy,
                printedOrigin = preview.origin,
                onFirstPaperDraw = { model.cardCapture.previewShown(preview.requestId) }, printedAcknowledged = preview.shown,
                readOnly = model.cards.isReadOnly(card.id), motion = rememberCardMotionPreview(model.stickers, card))
        }
    }
    removeCard?.let { card ->
        CardDeleteDialog("删除这张拍立得？", card.title, { removeCard = null }, {
            removeCard = null
            actions.cardAction(card, CardMenuAction.DELETE)
        })
    }
    moveCard?.let { card ->
        CardAlbumTargetDialog(catalog.albums, albumContaining(catalog.albums, card.id)?.id, { moveCard = null }) { album ->
            moveCard = null
            actions.work({ model.cards.moveCards(listOf(card.id), album) }) {
                model.cardUi.focusCard(card.id, openPreview = false)
            }
        }
    }
    CardQuickExportDialog(actions)
}
