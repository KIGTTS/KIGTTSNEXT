@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp

/** Only presentation interpolates; the actual enabled flag still gates input immediately. */
@Composable
internal fun enabledProgress(enabled: Boolean): Float {
    val progress by animateFloatAsState(if (enabled) 1f else 0f,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "control-enabled")
    return progress
}

@Composable
fun ButtonColors.animateEnabled(enabled: Boolean): ButtonColors {
    val progress = enabledProgress(enabled)
    val container = lerp(disabledContainerColor, containerColor, progress)
    val content = lerp(disabledContentColor, contentColor, progress)
    return copy(containerColor = container, contentColor = content,
        disabledContainerColor = container, disabledContentColor = content)
}

@Composable
fun IconButtonColors.animateEnabled(enabled: Boolean): IconButtonColors {
    val progress = enabledProgress(enabled)
    val container = lerp(disabledContainerColor, containerColor, progress)
    val content = lerp(disabledContentColor, contentColor, progress)
    return copy(containerColor = container, contentColor = content,
        disabledContainerColor = container, disabledContentColor = content)
}

@Composable
fun Modifier.enabledContentMotion(enabled: Boolean, disabledAlpha: Float = .38f): Modifier {
    val progress = enabledProgress(enabled)
    return graphicsLayer { alpha = disabledAlpha + (1f - disabledAlpha) * progress }
}
