@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

internal val LocalPolaroidSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalPolaroidPhotoVisible = compositionLocalOf { true }

/** One paper moves between browsing and management slots, retaining its image and caption. */
@Composable internal fun Modifier.sharedPolaroidPhoto(id: String): Modifier {
    val scope = LocalPolaroidSharedScope.current ?: return this
    val visible = LocalPolaroidPhotoVisible.current
    return with(scope) {
        sharedElementWithCallerManagedVisibility(rememberSharedContentState("polaroid/$id"), visible,
            boundsTransform = { _, _ -> tween(340, easing = FastOutSlowInEasing) }).scalePolaroidToLookahead()
    }
}
