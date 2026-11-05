package com.kigtts.next.ui.cards.camera

import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Receipts belong to one visit; a slow older save cannot replace a more recent preview. */
internal class CameraVisitGalleryPreview {
    var uri by mutableStateOf<Uri?>(null); private set
    private var sequence = 0L
    private var displayed = 0L
    fun capture(): (Uri) -> Unit {
        val ticket = ++sequence
        return { saved -> if (ticket >= displayed) { displayed = ticket; uri = saved } }
    }
}

@Composable
internal fun CameraGalleryThumbnail(uri: Uri?) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        preview = uri?.let { source ->
            withContext(Dispatchers.IO) { runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(source, Size(192, 192), null)
                else {
                    @Suppress("DEPRECATION")
                    if (context.contentResolver.getType(source)?.startsWith("video/") == true)
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(source),
                            MediaStore.Video.Thumbnails.MINI_KIND, null)
                    else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(source),
                        MediaStore.Images.Thumbnails.MINI_KIND, null)
                }
            }.getOrNull() }
        }
    }
    Crossfade(preview, animationSpec = tween(180), label = "camera-gallery-thumbnail") { bitmap ->
        if (bitmap != null) Image(remember(bitmap) { bitmap.asImageBitmap() }, null,
            Modifier.size(44.dp).testTag("camera-gallery-thumbnail"), contentScale = ContentScale.Crop)
    }
}
