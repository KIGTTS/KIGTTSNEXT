package com.kigtts.next.ui.plugins

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.conflate

/** A nested thought bubble follows its own end and never takes the outer list's scroll. */
@Composable internal fun rememberChatInnerScroll(contentKey: Any?): ScrollState {
    val scroll = rememberScrollState()
    val held by scroll.interactionSource.collectIsDraggedAsState()
    var following by remember(contentKey) { mutableStateOf(true) }
    val halfLine = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toPx() / 2 }
    LaunchedEffect(held) { if (held) following = false }
    LaunchedEffect(scroll) {
        snapshotFlow { Triple(scroll.maxValue, scroll.isScrollInProgress, held) }.conflate().collect { (maximum, moving, dragging) ->
            if (!dragging && !moving) {
                if (following) scroll.scrollTo(maximum)
                else if (maximum - scroll.value <= halfLine) { following = true; scroll.animateScrollTo(maximum) }
            }
        }
    }
    // Layout changes maxValue after the new text is measured; it is the only follow signal.
    return scroll
}
