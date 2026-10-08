@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*

@Composable
fun DisplaySettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    SettingsLayout {
        SettingsCard {
            Text("对比度", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ContrastLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(selected = settings.contrast == level, onClick = feedbackClick { onChange(settings.copy(contrast = level)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ContrastLevel.entries.size),
                        modifier = Modifier.semantics { contentDescription = "对比度：${level.label}" }) { Text(level.label) }
                }
            }
            Text("默认、中、高三档，使用 Material 原生配色算法。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCard {
            ToggleSetting("更暗的深色模式", "使用黑色背景与更深的表面色调", settings.deeperDark, { onChange(settings.copy(deeperDark = it)) })
        }
        SettingsCard {
            Row { Text("圆角大小", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Text("${settings.cornerRadius.toInt()} dp") }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CornerLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(selected = settings.cornerLevel == level, onClick = feedbackClick { onChange(settings.copy(cornerLevel = level)) },
                        shape = SegmentedButtonDefaults.itemShape(index, CornerLevel.entries.size),
                        modifier = Modifier.semantics { contentDescription = "圆角：${level.label}" }) { Text("${level.label} ${level.radius.toInt()}") }
                }
            }
            Text("小 4、中 14、大 24 dp，默认大；主界面卡片外圆角限制为 4–20 dp。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCard {
            Text("外观预览", style = MaterialTheme.typography.titleMedium)
            Text("这里是输入的文本", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = expressivePillShapes(), onClick = feedbackClick {}) { Text("朗读") }
                FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick {}) { Text("聆听") }
            }
        }
        OutlinedButton(shapes = expressivePillShapes(), onClick = feedbackClick {
            val defaults = ThemeSettings()
            onChange(settings.copy(contrast = defaults.contrast, deeperDark = defaults.deeperDark, cornerLevel = defaults.cornerLevel))
        }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认显示") }
    }
}
