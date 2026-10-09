@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*

@Composable
internal fun AppearanceDisplaySettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    SettingsGroup("显示与形状") {
        SettingItem("对比度", "增强文字、按钮与背景的区分度。", Icons.Default.Contrast, SettingPosition.FIRST) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ContrastLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(selected = settings.contrast == level, onClick = feedbackClick { onChange(settings.copy(contrast = level)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ContrastLevel.entries.size),
                        modifier = Modifier.semantics { contentDescription = "对比度：${level.label}" }) { Text(level.label) }
                }
            }
        }
        ToggleSetting("更暗的深色模式", "在深色主题下使用纯黑背景，让界面更暗。", Icons.Default.DarkMode,
            settings.deeperDark, { onChange(settings.copy(deeperDark = it)) }, position = SettingPosition.MIDDLE)
        SettingItem("圆角大小", "让卡片和按钮的边角更方正或圆润。", Icons.Default.RoundedCorner, SettingPosition.LAST) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CornerLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(selected = settings.cornerLevel == level, onClick = feedbackClick { onChange(settings.copy(cornerLevel = level)) },
                        shape = SegmentedButtonDefaults.itemShape(index, CornerLevel.entries.size),
                        modifier = Modifier.semantics { contentDescription = "圆角：${level.label}" }) { Text(level.label) }
                }
            }
        }
    }
    SettingsGroup("预览") {
        SettingItem("外观预览", "查看当前颜色与圆角效果。", Icons.Default.Preview, SettingPosition.SINGLE) {
            Text("这里是输入的文本", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = expressivePillShapes(), onClick = feedbackClick {}) { Text("朗读") }
                FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick {}) { Text("聆听") }
            }
        }
    }
}
