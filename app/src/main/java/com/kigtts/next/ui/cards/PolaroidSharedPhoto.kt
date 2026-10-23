@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal val LocalPolaroidSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalPolaroidPhotoVisible = compositionLocalOf { true }
internal val LocalPolaroidOverlayOrder = compositionLocalOf<Float?> { null }

/** One paper moves between browsing and management slots, retaining its image and caption. */
@Composable internal fun Modifier.sharedPolaroidPhoto(id: String): Modifier {
    val scope = LocalPolaroidSharedScope.current ?: return this
    val visible = LocalPolaroidPhotoVisible.current
    val geometry = LocalCardManagementGeometry.current
    val sourceOrder = LocalPolaroidOverlayOrder.current
    if (sourceOrder != null) geometry?.overlayOrder?.set(id, sourceOrder)
    val opacity = animateFloatAsState(if (visible) 1f else 0f, tween(340), label = "unmatched-paper")
    return with(scope) {
        val shared = rememberSharedContentState("polaroid/$id")
        // The pager's z-order is otherwise lost when every photo moves into the common overlay.
        // A source without a laid-out destination must fade, not vanish on the first frame.
        sharedElementWithCallerManagedVisibility(shared, visible,
            zIndexInOverlay = geometry?.overlayOrder?.get(id) ?: sourceOrder ?: 0f,
            boundsTransform = { _, _ -> tween(340, easing = FastOutSlowInEasing) })
            .graphicsLayer { alpha = if (shared.isMatchFound) 1f else opacity.value }
            .scalePolaroidToLookahead()
    }
}
