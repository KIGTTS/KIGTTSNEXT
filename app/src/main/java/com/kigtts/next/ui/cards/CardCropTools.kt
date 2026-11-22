@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import com.kigtts.next.cards.*
import com.kigtts.next.ui.cards.camera.CameraWatermarkControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CardCropTools(enabled: Boolean, openCrop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("裁剪照片", Icons.Default.Crop, openCrop, enabled = enabled)
        Text(uiText("photo.crop_zoom"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CardFrameTools(card: PolaroidCard, enabled: Boolean, change: (PolaroidCard) -> Unit,
    frameColor: () -> Unit, frames: List<PolaroidFrameStyle>) {
    val spec = PhotoFrameSpec.from(card.frameStyleId)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("camera.frame_watermark"), style = MaterialTheme.typography.titleSmall)
        if (spec != null) {
            val context = LocalContext.current
            val choices by produceState(emptyList<CameraWatermarkAsset>(), context) {
                value = withContext(Dispatchers.IO) { CameraWatermarkLibrary(context).choices() }
            }
            var selectedId by remember(card.id) { mutableStateOf<String?>(null) }
            val watermark = spec.watermark
            val active = selectedId ?: choices.firstOrNull { it.defaults == watermark }?.id
                ?: choices.firstOrNull { it.defaults.layout == watermark?.layout }?.id
            fun set(value: CameraWatermarkStyle?) = change(card.copy(
                frameStyleId = spec.copy(style = value?.encode() ?: "none").id))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(spec.style == "none", feedbackClick { selectedId = null; set(null) }, label = { Text(uiText("photo.no_watermark")) }, enabled = enabled)
                choices.forEach { asset -> FilterChip(asset.id == active, feedbackClick {
                    if (asset.id != active) { selectedId = asset.id; set(asset.defaults) }
                }, label = { Text(asset.name) }, enabled = enabled) }
            }
            if (watermark != null) CameraWatermarkControls(watermark, enabled, { set(it) })
            return@Column
        }
        val frame = frames.firstOrNull { it.id == card.frameStyleId }
        Text(frame?.name ?: uiText("photo.legacy_frame"), style = MaterialTheme.typography.bodyMedium)
        if (frame?.color == null) PillButton("纸框颜色", Icons.Default.Palette, frameColor, enabled = enabled)
    }
}
