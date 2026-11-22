package com.kigtts.next.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

val LocalBackdropViewport = compositionLocalOf { Rect.Zero }

/** Draw the cached image in the same window coordinates as the background behind the sheet. */
@Composable internal fun DrawerBackdrop(settings: ThemeSettings, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewport = LocalBackdropViewport.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val bitmap by produceState(AppearanceBackgrounds.peek(context, settings.background, true)?.asImageBitmap(), settings.background) {
        value = withContext(Dispatchers.IO) { runCatching {
            AppearanceBackgrounds.load(context, settings.background, true)?.asImageBitmap()
        }.getOrNull() }
    }
    val colors = MaterialTheme.colorScheme
    val paint = LocalThemePaint.current
    Canvas(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        val current = paint?.palette?.colors ?: colors
        val tint = current.surfaceContainer
        val image = bitmap
        if (image != null && viewport.width > 0 && viewport.height > 0) {
            val scale = max(viewport.width / image.width, viewport.height / image.height)
            withTransform({
                translate(viewport.left - origin.x + (viewport.width - image.width * scale) / 2,
                    viewport.top - origin.y + (viewport.height - image.height * scale) / 2)
                scale(scale, scale, pivot = Offset.Zero)
            }) { drawImage(image) }
            drawRect(current.background.copy(alpha = settings.backgroundDim))
            drawRect(tint.copy(alpha = .78f))
        } else drawRect(tint)
    }
}
