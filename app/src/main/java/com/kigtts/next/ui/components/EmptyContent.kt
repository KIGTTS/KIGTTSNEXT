package com.kigtts.next.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Keep this beside the content, including when hidden, so both directions can fade. */
@Composable
internal fun EmptyContent(visible: Boolean, icon: ImageVector, title: String,
    modifier: Modifier = Modifier, description: String? = null) {
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(visible) { opacity.animateTo(if (visible) 1f else 0f, tween(if (visible) 200 else 160)) }
    val retained by remember(visible) { derivedStateOf { visible || opacity.value > 0f } }
    if (retained) {
        BoxWithConstraints(modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }, contentAlignment = Alignment.Center) {
            val compact = maxHeight < 160.dp
            Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(if (compact) 32.dp else 48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (!compact && !description.isNullOrBlank()) Text(description, Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
