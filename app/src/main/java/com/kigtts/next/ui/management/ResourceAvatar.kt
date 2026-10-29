package com.kigtts.next.ui.management

import com.kigtts.next.media.decodeResourceArtwork
import com.kigtts.next.media.isWhiteLineArtwork
import com.kigtts.next.media.decodeResourceVector
import com.kigtts.next.media.ResourceVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.vector
import com.kigtts.next.ui.components.SvgResourcePainter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface AvatarArtwork {
    data class Vector(val value: ResourceVector) : AvatarArtwork
    data class Pixels(val bitmap: ImageBitmap, val whiteLine: Boolean) : AvatarArtwork
}

@Composable
internal fun ResourceAvatar(item: ManagementItem, resolve: (PluginResource) -> File?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val file = item.image?.let { runCatching { resolve(it) }.getOrNull() }
    val version = file?.lastModified()
    val artwork by produceState<AvatarArtwork?>(null, file?.path, version) {
        value = withContext(Dispatchers.IO) {
            decodeResourceVector(file)?.let(AvatarArtwork::Vector)
                ?: decodeResourceArtwork(file)?.let { AvatarArtwork.Pixels(it.asImageBitmap(), it.isWhiteLineArtwork()) }
        }
    }
    if (artwork != null) {
        val painter: Painter = remember(artwork) { when (val art = artwork!!) {
            is AvatarArtwork.Vector -> SvgResourcePainter(art.value.picture)
            is AvatarArtwork.Pixels -> BitmapPainter(art.bitmap)
        } }
        val lineArt = when (val art = artwork!!) {
            is AvatarArtwork.Vector -> item.image?.monochrome == true || art.value.whiteLine
            is AvatarArtwork.Pixels -> art.whiteLine
        }
        Surface(Modifier.size(size).testTag("managed-avatar/" + item.id), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Image(painter, "${item.title}图标", Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                colorFilter = if (lineArt) ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer) else null)
        }
    }
    else Surface(Modifier.size(size).testTag("managed-avatar/" + item.id), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(item.icon.vector(item.symbol), null, Modifier.size(size * .55f), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
