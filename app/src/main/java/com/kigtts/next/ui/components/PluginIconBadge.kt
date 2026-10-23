package com.kigtts.next.ui.components

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kigtts.plugin.api.BadgeTone
import com.kigtts.plugin.api.PluginBadge

@Composable
internal fun PluginIconBadge(badge: PluginBadge?, contained: Boolean = false, content: @Composable () -> Unit) {
    if (badge == null) content()
    else Layout(content = {
        Box { content() }
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
        val count = badge.count
        val modifier = Modifier.semantics { contentDescription = badge.description }
        if (count == null) Badge(modifier, containerColor = background, contentColor = foreground)
        else Badge(modifier, containerColor = background, contentColor = foreground) {
            Text(if (count > 99) "99+" else count.toString())
        }
    }) { measurables, constraints ->
        val icon = measurables[0].measure(constraints)
        val bubble = measurables[1].measure(Constraints())
        layout(icon.width, icon.height) {
            icon.placeRelative(0, 0)
            // End-align the whole bubble, not its leading edge: 99+ must not cover the
            // next setting label or get clipped by the button's fixed touch target.
            val offset = if (contained) 0 else 4.dp.roundToPx()
            bubble.placeRelative(icon.width - bubble.width + offset, -offset)
        }
    }
}
