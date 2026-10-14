@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

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
    camera: () -> Unit, gallery: () -> Unit, addText: () -> Unit, addSticker: () -> Unit, addImage: () -> Unit, addQrCode: () -> Unit,
    frameColor: () -> Unit, selected: CardLayer?, editText: () -> Unit, layerColor: () -> Unit,
    deleteLayer: () -> Unit, forwardLayer: () -> Unit, mode: CardEditorMode, selectMode: (CardEditorMode) -> Unit,
    frames: List<PolaroidFrameStyle>, openLibrary: () -> Unit, textEditing: Boolean = false,
    updateTextLayer: (CardLayer) -> Unit = {}, finishText: () -> Unit = {},
    showTabs: Boolean = true, stickerLibrary: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTabs) CardEditorTabs(mode, selectMode)
        if (mode == CardEditorMode.CROP) CardCropTools(card, enabled, change, camera, gallery, frameColor, frames)
        else if (mode == CardEditorMode.FRAME) CardFrameTools(card, enabled, change, frameColor, frames)
        else if (mode == CardEditorMode.STICKERS) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("添加文字", Icons.Default.TextFields, addText, enabled = enabled)
                PillButton("贴纸", Icons.Default.Collections, addSticker, enabled = enabled)
                PillButton("表情图片", Icons.Default.AddPhotoAlternate, addImage, enabled = enabled)
                PillButton("二维码", Icons.Default.QrCode2, addQrCode, Modifier.testTag("card-add-qr"), enabled = enabled)
                PillButton("管理贴纸库", Icons.Default.FolderOpen, openLibrary, enabled = enabled)
            }
            if (selected == null) stickerLibrary()
            AnimatedVisibility(selected != null) {
                Surface(shape = homeCardShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                        if (selected?.kind == CardLayerKind.TEXT) {
                            ToolButton("编辑图层文字", Icons.Default.Edit, editText, Modifier.width(32.dp), enabled = enabled)
                            ToolButton("文字颜色", Icons.Default.Palette, layerColor, Modifier.width(32.dp), enabled = enabled)
                        }
                        ToolButton("移到最上层", Icons.Default.FlipToFront, forwardLayer, Modifier.width(32.dp), enabled = enabled)
                        ToolButton("移除选中图层", Icons.Default.DeleteOutline, deleteLayer, Modifier.width(32.dp), enabled = enabled)
                    }
                }
            }
            if (selected?.kind == CardLayerKind.TEXT) CardEditorSection("贴纸文字", Icons.Default.TextFields,
                "card-sticker-text-section", forceExpanded = textEditing) {
                CardTextLayerEditor(selected, enabled, updateTextLayer, finishText)
            }
            Text("点选后拖动圆形手柄，或用双指移动、缩放和旋转", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (mode == CardEditorMode.TEXT) CardEditorSection("照片文字", Icons.Default.TextFields, "card-photo-text-section", forceExpanded = true) {
            OutlinedTextField(card.title, { if (it.length <= 128) change(card.copy(title = it)) },
                Modifier.fillMaxWidth().testTag("card-title"), label = { Text("照片名称（可选）") }, enabled = enabled, singleLine = true)
            OutlinedTextField(card.description, { if (it.length <= 8192) change(card.copy(description = it)) },
                Modifier.fillMaxWidth().testTag("card-description"), label = { Text("照片说明（可选）") }, enabled = enabled, minLines = 2, maxLines = 4)
        }
        Spacer(Modifier.height(20.dp))
    }
}
