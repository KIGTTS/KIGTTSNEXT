package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.state.*

@Composable
fun SettingsScreen(open: (Destination) -> Unit) {
    SettingsLayout {
        Text("按分类调整界面与交互", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsCategory.entries.forEach { category ->
            val icon = when (category) {
                SettingsCategory.COLORS -> Icons.Default.Palette
                SettingsCategory.DISPLAY -> Icons.Default.RoundedCorner
                SettingsCategory.FEEDBACK -> Icons.Default.Vibration
                SettingsCategory.EXPERIMENTAL -> Icons.Default.Science
            }
            CategoryEntry(category.title, category.summary, icon) { open(Destination.SettingsDetail(category)) }
        }
        CategoryEntry("插件管理", "引擎、语音输入和插件配置", Icons.Default.Extension) { open(Destination.Plugins) }
    }
}

@Composable
private fun CategoryEntry(title: String, summary: String, icon: ImageVector, click: () -> Unit) {
    Surface(onClick = feedbackClick(click), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
