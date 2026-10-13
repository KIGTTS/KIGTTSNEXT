@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

@Composable internal fun CardManagementPhoto(card: PolaroidCard, selected: Boolean, enabled: Boolean,
    imageFile: (String) -> File?, frame: PolaroidFrameStyle?, select: () -> Unit,
    modifier: Modifier, grip: Modifier, lifted: Boolean, originId: String? = null) {
    val elevation by animateDpAsState(if (lifted) 10.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "card-sort-elevation")
    Surface(feedbackClick(select), modifier.testTag("manage-photo/" + card.id).semantics {
        this.selected = selected; contentDescription = card.title.ifBlank { "拍立得" }
    },
        enabled = enabled, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = elevation, border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Box {
            val file = card.imageId?.let(imageFile)
            val target = rememberCardImage(file, 512)
            val geometry = LocalCardManagementGeometry.current
            val sourceMaximum = remember(card.id, originId, file?.path) {
                if (card.id == originId) geometry?.sources?.get(card.id)?.takeIf { it.isAttached }
                    ?.boundsInWindow()?.width?.let { cardImageDecodeSize(it, 1200) } ?: 512 else 512
            }
            val source = rememberCardImage(file, sourceMaximum)
            PolaroidCanvas(card, imageFile, Modifier.fillMaxWidth(), frame = frame, decodeMaximum = 512, photoOverride = target ?: source)
            Icon(Icons.Default.DragIndicator, "拖动照片", Modifier.align(Alignment.TopStart).padding(4.dp).size(28.dp)
                .testTag("manage-photo-grip/" + card.id).then(grip), tint = MaterialTheme.colorScheme.primary)
            CardSelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

@Composable internal fun CardManagementAlbum(album: PolaroidAlbum, selected: Boolean, enabled: Boolean,
    select: () -> Unit, modifier: Modifier, grip: Modifier, lifted: Boolean) {
    val elevation by animateDpAsState(if (lifted) 10.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "album-sort-elevation")
    Surface(feedbackClick(select), modifier.fillMaxWidth().testTag("manage-album/" + album.id).semantics { this.selected = selected },
        enabled = enabled, shape = homeCardShape(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shadowElevation = elevation) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.DragIndicator, "拖动相册", Modifier.size(32.dp).testTag("manage-album-grip/" + album.id).then(grip))
            Icon(Icons.Default.PhotoAlbum, null, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(album.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${album.cardIds.size} 张照片", style = MaterialTheme.typography.bodySmall)
            }
            CardSelectionMark(selected)
        }
    }
}

@Composable private fun CardSelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.size(28.dp), shape = androidx.compose.foundation.shape.CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(contentAlignment = Alignment.Center) { Icon(if (selected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
            null, Modifier.size(18.dp)) }
    }
}
