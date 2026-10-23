@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            PillButton("拍照", Icons.Default.CameraAlt, camera, Modifier.weight(1f), enabled = enabled)
            PillButton("换照片", Icons.Default.PhotoLibrary, gallery, Modifier.weight(1f), enabled = enabled)
        }
        Text("相纸规格", style = MaterialTheme.typography.titleSmall)
        ChoiceButtonGroup("相纸规格", editablePolaroidFormats, format,
            { when (it) { PolaroidFormat.MINI -> "Mini"; PolaroidFormat.WIDE -> "Wide"; else -> "Square" } },
            { change(selectPolaroidFormat(card, it, frames)) }, enabled = enabled)
        Text("留白背景", style = MaterialTheme.typography.titleSmall)
        ChoiceButtonGroup("留白背景", PhotoBackdropMode.entries, card.photoBackdrop,
            { if (it == PhotoBackdropMode.GRAY) "灰色" else "模糊" }, { change(card.copy(photoBackdrop = it)) }, enabled = enabled)
        PillButton("重置照片位置", Icons.Default.RestartAlt, { change(card.copy(photoX = .5, photoY = .5, photoZoom = 1.0)) }, enabled = enabled)
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
        val choices = frames.filter { it.format == format }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxOf(maxWidth, 104.dp * choices.size)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ChoiceButtonGroup("边框样式", choices.map { it.id }, card.frameStyleId,
                    { id -> choices.first { it.id == id }.name },
                    { change(card.copy(frameStyleId = it)) }, Modifier.width(width), enabled = enabled)
            }
        }
        if (currentFrame?.color == null)
            PillButton("纸框颜色", Icons.Default.Palette, frameColor, enabled = enabled)
    }
}
