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
import com.kigtts.next.cards.PhotoFrameSpec

@Composable
internal fun CardCropTools(enabled: Boolean, openCrop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("裁剪照片", Icons.Default.Crop, openCrop, enabled = enabled)
        Text("双指缩放和平移预览", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CardFrameTools(card: PolaroidCard, enabled: Boolean, change: (PolaroidCard) -> Unit,
    frameColor: () -> Unit, frames: List<PolaroidFrameStyle>) {
    val spec = PhotoFrameSpec.from(card.frameStyleId)
    val currentFrame = frames.firstOrNull { it.id == (spec?.style ?: card.frameStyleId) }
    val format = polaroidFormat(card, currentFrame)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("边框水印", style = MaterialTheme.typography.titleSmall)
        if (spec != null) {
            val choices = listOf("none", "classic", "night", "slim", "soft") + frames.filter { it.owner != null }.map { it.id }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { id -> FilterChip(id == spec.style, feedbackClick { change(card.copy(frameStyleId = spec.copy(style = id).id,
                    photoX = .5, photoY = .5, photoZoom = 1.0)) }, label = { Text(if (id == "none") "无水印" else frames.firstOrNull { it.id == id }?.name ?: id) }, enabled = enabled) }
            }
            PillButton("边条颜色", Icons.Default.Palette, frameColor, enabled = enabled)
            Text("边条加在照片外侧，保留完整画面", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
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
