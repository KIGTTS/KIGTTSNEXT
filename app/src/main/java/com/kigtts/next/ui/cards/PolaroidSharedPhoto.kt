@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal val LocalPolaroidSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalPolaroidPhotoVisible = compositionLocalOf { true }
internal val LocalPolaroidOverlayOrder = compositionLocalOf<Float?> { null }
internal val LocalPolaroidDecoration = compositionLocalOf { false }

/** One paper moves between browsing and management slots, retaining its image and caption. */
@Composable internal fun Modifier.sharedPolaroidPhoto(id: String): Modifier {
    val targetPose = LocalPolaroidVisualPose.current
    val scope = LocalPolaroidSharedScope.current ?: return graphicsLayer {
        val pose = targetPose(); rotationZ = pose.rotation; alpha = pose.opacity
    }
    val visible = LocalPolaroidPhotoVisible.current
    val geometry = LocalCardManagementGeometry.current
    val entering = LocalPolaroidEntering.current
    val sourceOrder = LocalPolaroidOverlayOrder.current
    if (sourceOrder != null) geometry?.overlayOrder?.set(id, sourceOrder)
    val decoration = LocalPolaroidDecoration.current
    val opacity = remember(id) { Animatable(if (visible && !entering) 1f else 0f) }
    LaunchedEffect(visible) { opacity.animateTo(if (visible) 1f else 0f, tween(340)) }
    return with(scope) {
        // Neighboring stack papers fade in their own slots. Only the focused paper
        // travels to the grid/sort endpoint; neighbors never straighten and fly across it.
        val shared = rememberSharedContentState(if (decoration) "polaroid-decoration/$id" else "polaroid/$id")
        // The pager's z-order is otherwise lost when every photo moves into the common overlay.
        // A source without a laid-out destination must fade, not vanish on the first frame.
        sharedElementWithCallerManagedVisibility(shared, visible,
            zIndexInOverlay = geometry?.overlayOrder?.get(id) ?: sourceOrder ?: 0f,
            boundsTransform = { _, _ -> tween(340, easing = FastOutSlowInEasing) })
            .graphicsLayer {
                val pose = targetPose()
                rotationZ = pose.rotation
                alpha = pose.opacity * if (shared.isMatchFound) 1f else opacity.value
            }
            .scalePolaroidToLookahead()
    }
}
