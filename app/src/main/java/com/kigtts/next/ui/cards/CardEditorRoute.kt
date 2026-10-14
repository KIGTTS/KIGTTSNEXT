package com.kigtts.next.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination

@Composable
internal fun CardEditorRoute(model: AppViewModel, destination: Destination.CardEditor) {
    val result by model.cardCapture.result.collectAsStateWithLifecycle()
    val photo = result?.takeIf { it.cardId == destination.cardId }
    CardPageScaffold("编辑拍立得", model::navigateBack, subtitle = null) {
        CardEditorScreen(model.cards, model.stickers, destination.cardId, model::message,
            openLibrary = { model.navigate(Destination.StickerLibrary()) },
            openCamera = { model.cardCapture.open(destination.cardId) },
            cameraImageId = photo?.imageId, cameraOriginalImageId = photo?.originalImageId,
            cameraFrameStyleId = photo?.frameStyleId, cameraRequestVersion = photo?.version ?: 0L)
    }
}
