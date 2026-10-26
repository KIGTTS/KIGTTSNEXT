package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.management.*
import com.kigtts.plugin.api.*
import java.io.File

@Composable internal fun PluginCatalogEntry(item: ManagementItem, action: ManagementProgressAction?,
    active: Boolean, canInvoke: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large) {
    Surface(onClick = feedbackClick { if (canInvoke) item.primaryAction?.let { invoke(it, emptyMap()) } },
        enabled = active, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().testTag("managed-item/" + item.id)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResourceAvatar(item, resolve, 56.dp)
            ManagedItemHeading(item, Modifier.weight(1f))
            val guarded: (String, Map<String, String>) -> Unit = { id, inputs -> if (canInvoke) invoke(id, inputs) }
            if (action != null) InlineProgressButton(item.id, action, active, guarded)
            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
            if (item.actions.isNotEmpty()) CatalogItemMenu(item, active, guarded)
        }
    }
}
