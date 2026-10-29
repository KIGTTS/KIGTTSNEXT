package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.IdentityCard
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.ui.identity.PersonalCardView
import com.kigtts.plugin.api.PolaroidCard

/** The outgoing content replaces the identity preview rather than adding another panel underneath. */
@Composable
internal fun FaceSharePreview(identity: IdentityCard?, photos: List<PolaroidCard>, stickers: StickerRepository) {
    if (identity != null) {
        PersonalCardView(identity, stickers, Modifier.fillMaxWidth().testTag("face-share-identity"), interactive = false)
    } else BoxWithConstraints(Modifier.fillMaxWidth().height(240.dp).testTag("face-share-photos"), contentAlignment = Alignment.Center) {
        val previews = photos.take(3)
        previews.indices.reversed().forEach { index ->
            val photo = previews[index]
            val frame = stickers.frameStyle(photo)
            val aspect = com.kigtts.next.cards.polaroidPaperGeometry(photo, frame).aspectRatio.toFloat()
            val width = minOf(maxWidth - 40.dp, (maxHeight - 24.dp) * aspect).coerceAtLeast(1.dp)
            PolaroidCanvas(photo, stickers::imageFile, Modifier.width(width).graphicsLayer {
                rotationZ = when (index) { 1 -> -6f; 2 -> 5f; else -> 0f }
                translationX = when (index) { 1 -> -10.dp.toPx(); 2 -> 12.dp.toPx(); else -> 0f }
                translationY = index * 3.dp.toPx()
            }.testTag("face-share-photo-$index"), frame = frame, decodeMaximum = 640, paperElevation = 2.dp)
        }
        if (photos.size > 1) Surface(Modifier.align(Alignment.BottomEnd), shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("${photos.size} 张照片", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (photos.isEmpty()) Text("没有可分享的照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
