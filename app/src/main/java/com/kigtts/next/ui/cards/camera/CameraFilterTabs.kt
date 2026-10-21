@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton

internal enum class CameraFilterTab { FILTERS, EFFECTS }
@Composable internal fun CameraFilterTabs(current: CameraFilterTab, enabled: Boolean, vertical: Boolean, select: (CameraFilterTab) -> Unit) {
    val buttons: @Composable () -> Unit = {
        CameraFilterTab.entries.forEach { tab ->
            ToolButton(if (tab == CameraFilterTab.FILTERS) "滤镜" else "特效", if (tab == CameraFilterTab.FILTERS) Icons.Default.PhotoFilter else Icons.Default.AutoAwesome,
                { select(tab) }, Modifier.size(48.dp).semantics { role = Role.Tab; selected = current == tab }, size = 40.dp, enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (current == tab) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent))
        }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
    else Row(verticalAlignment = Alignment.CenterVertically) { buttons() }
}
