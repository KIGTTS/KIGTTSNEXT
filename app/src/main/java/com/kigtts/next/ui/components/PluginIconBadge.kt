package com.kigtts.next.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kigtts.plugin.api.BadgeTone
import com.kigtts.plugin.api.PluginBadge

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun PluginIconBadge(badge: PluginBadge?, contained: Boolean = false, content: @Composable () -> Unit) {
    // Keep the badge out of measurement. This prevents a dot/count from moving its
    // button, while AnimatedContent retains the old value long enough to fade it out.
    Box {
        content()
        AnimatedContent(
            targetState = badge,
            modifier = Modifier.align(Alignment.TopEnd).then(
                if (contained) Modifier else Modifier.offset(x = 4.dp, y = (-4).dp)
            ),
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .72f))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .72f))
            },
            label = "plugin-badge"
        ) { value ->
            if (value != null) AnimatedBadge(value)
        }
    }
}

@Composable
private fun AnimatedBadge(badge: PluginBadge) {
    val colors = MaterialTheme.colorScheme
    val background = when (badge.tone) {
        BadgeTone.THEME -> colors.primary
        BadgeTone.WARNING -> colors.error
        BadgeTone.CAUTION -> androidx.compose.ui.graphics.Color(0xFFFFD54F)
    }
    val foreground = when (badge.tone) {
        BadgeTone.THEME -> colors.onPrimary
        BadgeTone.WARNING -> colors.onError
        BadgeTone.CAUTION -> androidx.compose.ui.graphics.Color(0xFF352700)
    }
    val modifier = Modifier.semantics { contentDescription = badge.description }
    val count = badge.count
    if (count == null) Badge(modifier, containerColor = background, contentColor = foreground)
    else Badge(modifier, containerColor = background, contentColor = foreground) {
        Text(if (count > 99) "99+" else count.toString())
    }
}
