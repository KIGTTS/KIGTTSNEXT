package com.kigtts.next.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import com.kigtts.plugin.api.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination

@Composable
internal fun CardEditorRoute(model: AppViewModel, destination: Destination.CardEditor) {
    val result by model.cardCapture.result.collectAsStateWithLifecycle()
    val photo = result?.takeIf { it.cardId == destination.cardId }
    val identities by model.identities.items.collectAsStateWithLifecycle()
    val designs by model.stickers.identityDesigns.items.collectAsStateWithLifecycle()
    var photoSelected by remember { mutableStateOf<((PolaroidCard) -> Unit)?>(null) }
    val personal = identities.firstOrNull { it.designId == destination.cardId }
    CardPageScaffold(if (personal != null) "编辑名片背景" else "编辑拍立得", model::navigateBack, subtitle = null, avoidKeyboard = false) {
        CardEditorScreen(model.cards, model.stickers, destination.cardId, model::message,
            openLibrary = { model.navigate(Destination.StickerLibrary()) },
            openCamera = { model.cardCapture.open(destination.cardId) },
            cameraImageId = photo?.imageId, cameraOriginalImageId = photo?.originalImageId, cameraPhotoStyle = photo?.photoStyle,
            cameraFrameStyleId = photo?.frameStyleId, cameraRequestVersion = photo?.version ?: 0L,
            cameraPhotoX = photo?.photoX ?: .5, cameraPhotoY = photo?.photoY ?: .5, cameraPhotoZoom = photo?.photoZoom ?: 1.0,
            identity = personal, personalDesign = designs.firstOrNull { it.id == destination.cardId },
            savePersonal = { design, identity ->
                val previous = model.stickers.identityDesigns.get(design.id)
                model.stickers.identityDesigns.put(design)
                try { model.identities.save(identity); design }
                catch (failure: Exception) {
                    if (previous != null) model.stickers.identityDesigns.put(previous) else model.stickers.identityDesigns.remove(design.id)
                    throw failure
                }
            }, selectPhoto = { photoSelected = it })
    }
    photoSelected?.let { selected ->
        com.kigtts.next.ui.artwork.LibraryArtworkSelector(model, ArtworkSelectionRequest(ArtworkLibrary.POLAROIDS, "选择拍立得"),
            { photoSelected = null }) { ids ->
            ids.firstOrNull()?.let { id -> model.cards.cards.value.firstOrNull { it.id == id }?.let(selected) }
            photoSelected = null
        }
    }
}
