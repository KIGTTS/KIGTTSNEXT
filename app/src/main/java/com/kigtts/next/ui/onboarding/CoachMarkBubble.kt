package com.kigtts.next.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.ThemeText
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun CoachMarkBubble(title: String, detail: String, modifier: Modifier = Modifier,
    onPresented: () -> Unit, dismiss: () -> Unit, enable: (() -> Unit)? = null) {
    var placed by remember { mutableStateOf(false) }
    val show = remember { Animatable(0f) }
    var closing by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(placed) { if (placed) onPresented() }
    LaunchedEffect(placed, closing) {
        if (!placed) return@LaunchedEffect
        val choice = closing
        if (choice == null) show.animateTo(1f, tween(150))
        else {
            show.animateTo(0f, tween(120))
            if (choice) enable?.invoke() else dismiss()
        }
    }
    Surface(modifier.fillMaxWidth().onPlaced { placed = true }.graphicsLayer { alpha = show.value },
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 4.dp) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ThemeText(title, style = MaterialTheme.typography.titleSmall)
            ThemeText(detail, Modifier.padding(end = 6.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CoachMarkAction(uiText(if (enable == null) "onboarding.got_it" else "quick.not_now"), closing == null) {
                    if (closing == null) closing = false
                }
                if (enable != null) CoachMarkAction(uiText("action.enable"), closing == null) {
                    if (closing == null) closing = true
                }
            }
        }
    }
}

@Composable
private fun CoachMarkAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = feedbackClick(onClick))
        .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
