package com.kigtts.next.ui.media

import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.ui.components.MaterialSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun PhraseWebIcon(phrase: QuickPhrase, repository: MediaRepository, modifier: Modifier, surface: Color) {
    val asset = phrase.metadata["icon.asset"]
    val bitmap by produceState<android.graphics.Bitmap?>(null, asset) { value = asset?.let { withContext(Dispatchers.IO) { repository.icons.bitmap(it) } } }
    val thumbnail = phrase.metadata["icon.mode"] == "thumbnail"
    Crossfade(bitmap, modifier, animationSpec = tween(220), label = "manual-web-icon") { image ->
    val frame = image?.asImageBitmap()
    Box(Modifier.fillMaxSize()) {
        if (frame != null) {
            Image(frame, null, if (thumbnail) Modifier.fillMaxSize() else Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(36.dp),
                contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit)
            if (thumbnail) Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(surface.copy(alpha = .65f), surface.copy(alpha = .04f)))))
        } else Icon(MaterialSymbols.vector(phrase.metadata["icon.symbol"]) ?: Icons.Default.Language, null,
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .65f))
    }
    }
}
