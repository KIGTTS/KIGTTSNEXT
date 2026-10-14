package com.kigtts.next.ui.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

@Composable
internal fun CardCropTools(card: PolaroidCard, enabled: Boolean, change: (PolaroidCard) -> Unit,
    camera: () -> Unit, gallery: () -> Unit, frameColor: () -> Unit, frames: List<PolaroidFrameStyle>) {
    val currentFrame = frames.firstOrNull { it.id == card.frameStyleId }
    val format = polaroidFormat(card, currentFrame)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionOutlinedButton(camera, Modifier.weight(1f), enabled) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("拍照") }
            MotionOutlinedButton(gallery, Modifier.weight(1f), enabled) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("换照片") }
        }
        Text("相纸规格", style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup("相纸规格", editablePolaroidFormats, format,
            { when (it) { PolaroidFormat.MINI -> "Mini"; PolaroidFormat.WIDE -> "Wide"; else -> "Square" } },
            { change(selectPolaroidFormat(card, it, frames)) }, enabled = enabled)
        Text("留白背景", style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceGroup("留白背景", PhotoBackdropMode.entries, card.photoBackdrop,
            { if (it == PhotoBackdropMode.GRAY) "灰色" else "模糊" }, { change(card.copy(photoBackdrop = it)) }, enabled = enabled)
        MotionOutlinedButton({ change(card.copy(photoX = .5, photoY = .5, photoZoom = 1.0)) }, enabled = enabled) {
            Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("重置照片位置")
        }
        Text("双指移动和缩放照片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CardFrameTools(card: PolaroidCard, enabled: Boolean, change: (PolaroidCard) -> Unit,
    frameColor: () -> Unit, frames: List<PolaroidFrameStyle>) {
    val currentFrame = frames.firstOrNull { it.id == card.frameStyleId }
    val format = polaroidFormat(card, currentFrame)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("边框样式", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            frames.filter { it.format == format }.forEach { style -> FilterChip(card.frameStyleId == style.id,
                feedbackClick { change(card.copy(frameStyleId = style.id)) }, label = { Text(style.name) }, enabled = enabled) }
        }
        if (currentFrame?.color == null)
            MotionOutlinedButton(frameColor, enabled = enabled) { Icon(Icons.Default.Palette, null); Spacer(Modifier.width(8.dp)); Text("纸框颜色") }
    }
}
