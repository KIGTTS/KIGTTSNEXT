package com.kigtts.next.ui.media

import androidx.compose.runtime.staticCompositionLocalOf

/** Physical pixels measured from the real card controls; WebView converts to CSS viewport units. */
data class MediaSafeArea(val top: Int = 0, val right: Int = 0, val bottom: Int = 0, val left: Int = 0)
val LocalMediaSafeArea = staticCompositionLocalOf { MediaSafeArea() }
