@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun ChatStepHeader(title: String, icon: ImageVector, expanded: Boolean, running: Boolean,
    modifier: Modifier = Modifier, status: String = "", expandable: Boolean = true, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(enabled = expandable, role = Role.Button, onClick = feedbackClick(onClick)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (status.isNotBlank()) Text(status, Modifier.widthIn(max = 100.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (running) ContainedLoadingIndicator(Modifier.size(24.dp).testTag("chat-step-loading"))
        if (expandable) Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            if (expanded) "收起步骤" else "展开步骤", Modifier.size(20.dp))
    }
}
