package com.kigtts.next.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination

@Composable
internal fun CardEditorRoute(model: AppViewModel, destination: Destination.CardEditor) {
    val result by model.cardCapture.result.collectAsStateWithLifecycle()
    val photo = result?.takeIf { it.cardId == destination.cardId }
    val identities by model.identities.items.collectAsStateWithLifecycle()
    val designs by model.stickers.identityDesigns.items.collectAsStateWithLifecycle()
    val personal = identities.firstOrNull { it.designId == destination.cardId }
    CardPageScaffold(uiText(if (personal != null) "profile.change_background" else "nav.edit_image"),
        model::navigateBack, subtitle = null, avoidKeyboard = false, compactBar = true) {
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
            })
    }
}
