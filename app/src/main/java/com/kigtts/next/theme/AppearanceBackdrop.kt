package com.kigtts.next.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun AppearanceBackdrop(settings: ThemeSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(AppearanceBackgrounds.peek(context, settings.background), settings.background) {
        value = withContext(Dispatchers.IO) { AppearanceBackgrounds.load(context, settings.background) }
    }
    Box(modifier) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (bitmap == null) 1f else settings.backgroundDim)))
    }
}
