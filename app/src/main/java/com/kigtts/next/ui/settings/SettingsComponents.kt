package com.kigtts.next.ui.settings

import com.kigtts.next.ui.components.feedbackChange

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

internal enum class SettingPosition { SINGLE, FIRST, MIDDLE, LAST }

internal fun groupedSettingShape(position: SettingPosition, outer: CornerBasedShape): RoundedCornerShape {
    val inner = CornerSize(4.dp)
    return RoundedCornerShape(
        topStart = if (position in setOf(SettingPosition.SINGLE, SettingPosition.FIRST)) outer.topStart else inner,
        topEnd = if (position in setOf(SettingPosition.SINGLE, SettingPosition.FIRST)) outer.topEnd else inner,
        bottomStart = if (position in setOf(SettingPosition.SINGLE, SettingPosition.LAST)) outer.bottomStart else inner,
        bottomEnd = if (position in setOf(SettingPosition.SINGLE, SettingPosition.LAST)) outer.bottomEnd else inner)
}

@Composable
internal fun SettingsLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 12.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
internal fun SettingItem(title: String, summary: String, icon: ImageVector, position: SettingPosition,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}) {
    val shape = groupedSettingShape(position, MaterialTheme.shapes.large)
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing()
            }
            content()
        }
    }
    if (onClick == null) Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, content = body)
    else Surface(onClick = feedbackClick(onClick), modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, content = body)
}

@Composable
internal fun ToggleSetting(title: String, summary: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, position: SettingPosition = SettingPosition.SINGLE, enabled: Boolean = true) {
    SettingItem(title, summary, icon, position, trailing = {
        Switch(checked, onCheckedChange = feedbackChange(onChange), enabled = enabled,
            modifier = modifier.semantics { contentDescription = title })
    })
}
