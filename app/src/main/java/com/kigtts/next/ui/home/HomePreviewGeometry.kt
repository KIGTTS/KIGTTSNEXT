package com.kigtts.next.ui.home

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/** Measured rectangles and control state; never screenshots of user text or media. */
@Stable class HomePreviewGeometry {
    val regions = mutableStateMapOf<String, Rect>()
    var automaticRead by mutableStateOf(false)
    fun update(key: String, rect: Rect) { if (regions[key] != rect) regions[key] = rect }
}
val LocalHomePreviewGeometry = staticCompositionLocalOf<HomePreviewGeometry?> { null }
internal val LocalRecordHomePreview = staticCompositionLocalOf { false }

@Composable internal fun Modifier.homePreviewRegion(key: String): Modifier {
    val owner = LocalHomePreviewGeometry.current ?: return this
    if (key != "viewport" && !LocalRecordHomePreview.current) return this
    DisposableEffect(owner, key) { onDispose { owner.regions.remove(key) } }
    return onGloballyPositioned {
        owner.update(key, Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())))
    }
}
