@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.PluginRegistry
import com.kigtts.next.ui.components.expressivePillShapes

@Composable
fun PluginsScreen(registry: PluginRegistry, open: (String, String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("插件", style = MaterialTheme.typography.headlineMedium)
        Text("朗读与聆听使用占位引擎。界面调试插件可按区域添加测试入口。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        registry.plugins.forEach { plugin ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plugin.descriptor.name, style = MaterialTheme.typography.titleLarge)
                    Text(plugin.descriptor.description)
                    Text("v${plugin.descriptor.version}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text("${plugin.contributions.actions.size} 个界面入口 · ${plugin.contributions.drawerEntries.size} 个页面入口", style = MaterialTheme.typography.bodySmall)
                    plugin.contributions.drawerEntries.forEach { entry ->
                        FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick { open(plugin.descriptor.id, entry.pageId) }) { Text("打开${entry.label}") }
                    }
                }
            }
        }
    }
}
