@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

@Composable
internal fun CardEditorFields(card: PolaroidCard, enabled: Boolean, change: (PolaroidCard) -> Unit,
    openCrop: () -> Unit, addText: () -> Unit, addSticker: () -> Unit, addImage: () -> Unit, addQrCode: () -> Unit,
    frameColor: () -> Unit, selected: CardLayer?, editText: () -> Unit, layerColor: () -> Unit,
    deleteLayer: () -> Unit, forwardLayer: () -> Unit, mode: CardEditorMode, selectMode: (CardEditorMode) -> Unit,
    frames: List<PolaroidFrameStyle>, openLibrary: () -> Unit, textEditing: Boolean = false,
    updateTextLayer: (CardLayer) -> Unit = {}, finishText: () -> Unit = {},
    showTabs: Boolean = true, stickerLibrary: @Composable () -> Unit = {},
    designTools: @Composable () -> Unit = {}, captureFields: @Composable () -> Unit = {}, backgroundTools: (@Composable () -> Unit)? = null,
    filterTools: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTabs) CardEditorTabs(mode, selectMode)
        if (mode == CardEditorMode.CROP) { if (backgroundTools != null) backgroundTools() else CardCropTools(card.imageId != null && enabled, openCrop) }
        else if (mode == CardEditorMode.FILTER) filterTools()
        else if (mode == CardEditorMode.FRAME) {
            CardFrameTools(card, enabled, change, frameColor, frames)
            if (com.kigtts.next.cards.PhotoFrameSpec.from(card.frameStyleId) == null) designTools()
        }
        else if (mode == CardEditorMode.STICKERS) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(uiText("photo.add_text"), Icons.Default.TextFields, addText,
                    Modifier.cardElementDragSource("new-text", CardLayer(kind = CardLayerKind.TEXT, text = "文字", width = .07)), enabled = enabled)
                PillButton(uiText("photo.sticker"), Icons.Default.Collections, addSticker, enabled = enabled)
                PillButton(uiText("photo.image_sticker"), Icons.Default.AddPhotoAlternate, addImage, enabled = enabled)
                PillButton(uiText("profile.qr"), Icons.Default.QrCode2, addQrCode, Modifier.testTag("card-add-qr"), enabled = enabled)
                PillButton(uiText("photo.sticker_manage"), Icons.Default.FolderOpen, openLibrary, enabled = enabled)
            }
            if (selected == null) stickerLibrary()
            AnimatedVisibility(selected != null) {
                Surface(shape = homeCardShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                        if (selected?.kind == CardLayerKind.TEXT) {
                            ToolButton(uiText("photo.edit_layer_text"), Icons.Default.Edit, editText, Modifier.width(32.dp), enabled = enabled)
                            ToolButton(uiText("photo.text_color"), Icons.Default.Palette, layerColor, Modifier.width(32.dp), enabled = enabled)
                        }
                        ToolButton(uiText("photo.layer_to_front"), Icons.Default.FlipToFront, forwardLayer, Modifier.width(32.dp), enabled = enabled)
                        ToolButton(uiText("photo.remove_layer"), Icons.Default.DeleteOutline, deleteLayer, Modifier.width(32.dp), enabled = enabled)
                    }
                }
            }
            if (selected?.kind == CardLayerKind.TEXT) CardEditorSection(uiText("photo.sticker_text"), Icons.Default.TextFields,
                "card-sticker-text-section", forceExpanded = textEditing) {
                CardTextLayerEditor(selected, enabled, updateTextLayer, finishText)
            }
            Text(uiText("photo.sticker_gestures"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (mode == CardEditorMode.TEXT) CardEditorSection(uiText("photo.photo_text"), Icons.Default.TextFields, "card-photo-text-section", forceExpanded = true) {
            OutlinedTextField(card.title, { if (it.length <= 128) change(card.copy(title = it)) },
                Modifier.fillMaxWidth().testTag("card-title"), label = { Text(uiText("photo.optional_title")) }, enabled = enabled, singleLine = true)
            OutlinedTextField(card.description, { if (it.length <= 8192) change(card.copy(description = it)) },
                Modifier.fillMaxWidth().testTag("card-description"), label = { Text(uiText("photo.optional_description")) }, enabled = enabled, minLines = 2, maxLines = 4)
        }
        if (mode == CardEditorMode.TEXT) captureFields()
        Spacer(Modifier.height(20.dp))
    }
}
