package com.kigtts.next.ui.cards.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Keep the Android preview instance across layout moves, while refreshing chrome/callback inputs every composition. */
@Composable
internal fun rememberCameraPreviewContent(view: PreviewView, session: PolaroidCameraSession,
    content: @Composable (Modifier, Boolean) -> Unit): @Composable (Modifier, Boolean) -> Unit {
    val latest = rememberUpdatedState(content)
    return remember(view, session) { { modifier, vertical -> latest.value(modifier, vertical) } }
}

/** The dock keeps its pager and touch owner when aspect changes move it over the viewfinder. */
@Composable internal fun rememberCameraControlsContent(content: @Composable (Boolean) -> Unit): @Composable (Boolean) -> Unit {
    val latest = rememberUpdatedState(content)
    return remember { { vertical -> latest.value(vertical) } }
}
