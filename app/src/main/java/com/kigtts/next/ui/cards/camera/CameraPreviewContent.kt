package com.kigtts.next.ui.cards.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Keep the Android preview instance across layout moves, while refreshing chrome/callback inputs every composition. */
@Composable
internal fun rememberCameraPreviewContent(view: PreviewView, session: PolaroidCameraSession,
    content: @Composable (Modifier, Boolean) -> Unit): @Composable (Modifier, Boolean) -> Unit {
    val latest = rememberUpdatedState(content)
    return remember(view, session) { movableContentOf<Modifier, Boolean> { modifier, vertical -> latest.value(modifier, vertical) } }
}
