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
    frames: List<PolaroidFrameStyle>, openLibrary: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(card.title, { if (it.length <= 128) change(card.copy(title = it)) },
            Modifier.fillMaxWidth().testTag("card-title"), label = { Text("标题") }, enabled = enabled, singleLine = true)
        OutlinedTextField(card.description, { if (it.length <= 8192) change(card.copy(description = it)) },
            Modifier.fillMaxWidth().testTag("card-description"), label = { Text("写点介绍（可选）") }, enabled = enabled, minLines = 2, maxLines = 4)
        CardEditorTabs(mode, selectMode)
        if (mode == CardEditorMode.CROP) CardCropTools(card, enabled, change, camera, gallery, frameColor, frames)
        else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionOutlinedButton(addText, enabled = enabled) { Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(6.dp)); Text("文字") }
                MotionOutlinedButton(addSticker, enabled = enabled) { Icon(Icons.Default.Collections, null); Spacer(Modifier.width(6.dp)); Text("贴纸") }
                MotionOutlinedButton(addImage, enabled = enabled) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("表情图片") }
                MotionOutlinedButton(addQrCode, Modifier.testTag("card-add-qr"), enabled = enabled) { Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("二维码") }
                MotionOutlinedButton(openLibrary, enabled = enabled) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("管理贴纸库") }
            }
            AnimatedVisibility(selected != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                        if (selected?.kind == CardLayerKind.TEXT) {
                            MotionIconButton(feedbackClick(editText), enabled = enabled) { Icon(Icons.Default.Edit, "编辑图层文字") }
                            MotionIconButton(feedbackClick(layerColor), enabled = enabled) { Icon(Icons.Default.Palette, "文字颜色") }
                        }
                        MotionIconButton(feedbackClick(forwardLayer), enabled = enabled) { Icon(Icons.Default.FlipToFront, "移到最上层") }
                        MotionIconButton(feedbackClick(deleteLayer), enabled = enabled) { Icon(Icons.Default.DeleteOutline, "移除选中图层") }
                    }
                }
            }
            Text("点选后拖动圆形手柄，或用双指移动、缩放和旋转", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
    }
}
