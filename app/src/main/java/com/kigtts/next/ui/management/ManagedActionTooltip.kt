@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.management

import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable internal fun ManagedActionTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface) { Text(label) } }, state = rememberTooltipState(), content = content)
}
