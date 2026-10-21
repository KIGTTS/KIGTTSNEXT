package com.kigtts.next.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.StickerUiTarget

/** Both the side entry and deeper plugin navigation consume the same one-shot native preview request. */
@Composable
fun CardHostScreen(model: AppViewModel) {
    val request by model.cardUi.previewRequest.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    CardsScreen(model.cards, model.stickers,
        { model.cardUi.open(StickerUiTarget.CARDS, it) }, { model.navigate(Destination.CardEditor(it)) }, model::message,
        onQuickImage = model::quickImageCard, openCamera = { model.cardCapture.open() },
        requestedCardId = request.cardId.takeIf { active }, requestVersion = if (active) request.version else 0L,
        openRequestedPreview = request.openPreview,
        onAlbumSelected = model.cardUi::selectAlbum, onPreviewRequestHandled = model.cardUi::previewHandled,
        openSystemCamera = { model.cardCapture.open(systemCamera = true) })
}
