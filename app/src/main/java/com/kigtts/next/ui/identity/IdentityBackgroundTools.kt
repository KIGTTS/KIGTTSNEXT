@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.identity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

@Composable internal fun IdentityBackgroundTools(card: PolaroidCard, identity: IdentityCard, enabled: Boolean,
    change: (PolaroidCard) -> Unit, changeIdentity: (IdentityCard) -> Unit,
    gallery: () -> Unit, polaroids: () -> Unit, backgroundColor: () -> Unit, maskColor: () -> Unit, frames: List<IdentityFrame>) {
    val primary = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xFFFFFFFFL
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("图片", Icons.Default.PhotoLibrary, gallery, Modifier.weight(1f), enabled = enabled)
            PillButton("拍立得", Icons.Default.PhotoCamera, polaroids, Modifier.weight(1f), enabled = enabled)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("仅用主色", Icons.Default.FormatColorFill, {
                change(card.copy(imageId = null, originalImageId = null, photoStyle = null, frameColor = primary))
            }, Modifier.weight(1f), enabled = enabled)
            PillButton("背景颜色", Icons.Default.Palette, backgroundColor, Modifier.weight(1f), enabled = enabled)
        }
        Text("花纹", style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val extent = maxOf(maxWidth, 84.dp * IdentityPatterns.names.size)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ChoiceButtonGroup("背景花纹", IdentityPatterns.names.keys.toList(), identity.pattern,
                    { IdentityPatterns.names.getValue(it) }, { changeIdentity(identity.copy(pattern = it)) }, Modifier.width(extent), enabled = enabled)
            }
        }
        Text("名片边框", style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val extent = maxOf(maxWidth, 96.dp * frames.size)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ChoiceButtonGroup("名片边框", frames.map { it.id }, identity.cardFrameId,
                    { id -> frames.firstOrNull { it.id == id }?.name ?: "无边框" },
                    { changeIdentity(identity.copy(cardFrameId = it)) }, Modifier.width(extent), enabled = enabled)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("渐变遮罩", Modifier.weight(1f))
            Switch(identity.gradient, feedbackChange { changeIdentity(identity.copy(gradient = it)) }, enabled = enabled)
        }
        if (identity.gradient) PillButton("渐变颜色", Icons.Default.Gradient, maskColor, enabled = enabled)
        if (card.imageId != null) PillButton("重置图片位置", Icons.Default.RestartAlt, {
            change(card.copy(photoX = .5, photoY = .5, photoZoom = 1.0, photoBackdrop = PhotoBackdropMode.BLUR))
        }, enabled = enabled)
    }
}
