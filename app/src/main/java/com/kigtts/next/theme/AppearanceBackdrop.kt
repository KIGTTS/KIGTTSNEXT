package com.kigtts.next.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

internal val LocalAppAppearanceSettings = staticCompositionLocalOf { ThemeSettings() }
internal val LocalSecondaryBackground = compositionLocalOf<ThemeSettings?> { null }

internal fun appearanceBackgroundDim(base: Float, blur: BackgroundBlur): Float =
    if (blur == BackgroundBlur.NONE) base.coerceIn(0f, 1f) else 1f - .8f * (1f - base.coerceIn(0f, 1f))

/** A single screen anchor keeps the image stationary behind scrolling cards and page chrome. */
internal fun backgroundImageRect(image: Size, viewport: Size, origin: Offset): Rect {
    val scale = max(viewport.width / image.width.coerceAtLeast(1f), viewport.height / image.height.coerceAtLeast(1f))
    val size = Size(image.width * scale, image.height * scale)
    return Rect(Offset((viewport.width - size.width) / 2, (viewport.height - size.height) / 2) - origin, size)
}

@Composable fun AppearanceBackdrop(settings: ThemeSettings, modifier: Modifier = Modifier,
    screenAnchored: Boolean = true, blur: BackgroundBlur = BackgroundBlur.NONE, scrimColor: Color? = null) {
    val context = LocalContext.current
    val window = LocalWindowInfo.current.containerSize
    val bitmap by produceState(AppearanceBackgrounds.peek(context, settings.background, blur), settings.background, blur) {
        value = withContext(Dispatchers.IO) { AppearanceBackgrounds.load(context, settings.background, blur) }
    }
    val dim = scrimColor ?: MaterialTheme.colorScheme.background
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        val image = bitmap?.asImageBitmap()
        clipRect {
            if (image != null) {
                val viewport = if (screenAnchored && window.width > 0 && window.height > 0) Size(window.width.toFloat(), window.height.toFloat()) else size
                val rect = backgroundImageRect(Size(image.width.toFloat(), image.height.toFloat()), viewport, if (screenAnchored) origin else Offset.Zero)
                withTransform({ translate(rect.left, rect.top); scale(rect.width / image.width, rect.height / image.height, Offset.Zero) }) { drawImage(image) }
            }
            drawRect(dim.copy(alpha = if (image == null) 1f else appearanceBackgroundDim(settings.backgroundDim, blur)))
        }
    }
}
