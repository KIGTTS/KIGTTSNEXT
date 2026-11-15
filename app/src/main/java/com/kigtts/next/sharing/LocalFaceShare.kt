package com.kigtts.next.sharing

import androidx.compose.runtime.staticCompositionLocalOf

internal data class FaceShareActions(val send: (List<String>) -> Unit, val receive: (() -> Unit)? = null)
internal val LocalFaceShare = staticCompositionLocalOf<FaceShareActions?> { null }
