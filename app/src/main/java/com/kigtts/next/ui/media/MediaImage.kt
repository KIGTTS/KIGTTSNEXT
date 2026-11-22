package com.kigtts.next.ui.media

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Alignment
import com.kigtts.next.media.MediaScale
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.kigtts.next.ui.navigation.LocalPageActive

/** Decode off the UI thread and cap the decoded dimension, including HEIF orientation. */
@Composable
fun MediaImage(file: File?, description: String, modifier: Modifier = Modifier, maximum: Int = 2048,
    scale: MediaScale = MediaScale.FIT, backdrop: Boolean = false, contentModifier: Modifier = Modifier) {
    var stamp by remember(file?.path) { mutableLongStateOf(file?.lastModified() ?: 0L) }
    if(LocalPageActive.current)stamp=file?.lastModified() ?: 0L
    var loaded by remember(file?.path, stamp, maximum) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, file?.path, stamp, maximum) {
        value = withContext(Dispatchers.IO) { file?.let { runCatching { decodeImage(it, maximum) }.getOrNull() } }
        loaded = true
    }
    Box(modifier.mediaReveal(bitmap!=null||loaded||file==null), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            if (backdrop && scale == MediaScale.FIT) BlurredBackdrop(bitmap!!.asAndroidBitmap(), Modifier.fillMaxSize())
            Image(bitmap!!, description, Modifier.fillMaxSize().then(contentModifier), contentScale = if (scale == MediaScale.FIT) ContentScale.Fit else ContentScale.Crop)
        } else Text(if (file == null) uiText("media.image_missing") else if (loaded) uiText("media.image_decoding_failed") else uiText("media.image_loading"), style = MaterialTheme.typography.bodySmall)
    }
}

internal fun decodeImage(file: File, maximum: Int): ImageBitmap? {
    return com.kigtts.next.media.MediaBitmapDecoder.decode(file, maximum)?.asImageBitmap()
}
