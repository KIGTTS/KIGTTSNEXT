@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.components.connectedShape
import com.kigtts.next.ui.components.compactTonalIconColors

internal enum class CameraFilterTab { FILTERS, EFFECTS }
@Composable internal fun CameraFilterTabs(current: CameraFilterTab, enabled: Boolean, vertical: Boolean, select: (CameraFilterTab) -> Unit) {
    val copy = rememberCameraCopy()
    val buttons: @Composable () -> Unit = {
        CameraFilterTab.entries.forEachIndexed { index, tab ->
            val first = index == 0
            val shape = connectedShape(if (vertical) CornerNeighbors(above = !first, below = first)
                else CornerNeighbors(start = !first, end = first), outer = 16.dp)
            val palette = compactTonalIconColors()
            ToolButton(if (tab == CameraFilterTab.FILTERS) copy.text("camera.filters") else copy.text("camera.effects"), if (tab == CameraFilterTab.FILTERS) Icons.Default.PhotoFilter else Icons.Default.AutoAwesome,
                { select(tab) }, Modifier.size(32.dp).semantics { role = Role.Tab; selected = current == tab }, enabled = enabled,
                shapes = IconButtonDefaults.shapes(shape, RoundedCornerShape(8.dp)),
                colors = if (current == tab) palette.copy(containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary) else palette)
        }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) { buttons() }
    else Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) { buttons() }
}
