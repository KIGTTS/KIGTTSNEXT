@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*

@Composable
fun ColorSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    var colorInput by rememberSaveable(settings.seed) { mutableStateOf(settings.hex) }
    val validHex = colorInput.removePrefix("#").matches(Regex("[0-9a-fA-F]{6}"))
    SettingsLayout {
        Text("颜色实时更新，设置自动保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsCard {
            Text("主题模式", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(selected = settings.mode == mode, onClick = feedbackClick { onChange(settings.copy(mode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        modifier = Modifier.semantics { contentDescription = "主题模式：" + mode.label }) { Text(mode.label) }
                }
            }
        }
        SettingsCard {
            ToggleSetting("壁纸取色", if (Build.VERSION.SDK_INT >= 31) "使用 Android 系统动态配色" else "需要 Android 12 或更高版本",
                settings.wallpaperEnabled, { onChange(settings.copy(wallpaperEnabled = it)) }, enabled = Build.VERSION.SDK_INT >= 31)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("主题色", style = MaterialTheme.typography.titleMedium)
            Text("关闭壁纸取色后，使用此颜色生成 MD3 配色。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(colorInput, { colorInput = it }, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("HEX 色值") }, isError = !validHex, enabled = !settings.wallpaperEnabled,
                    supportingText = { if (!validHex) Text("请输入 6 位十六进制颜色") })
                FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick {
                    onChange(settings.copy(seed = 0xFF000000 or colorInput.removePrefix("#").toLong(16)))
                }, enabled = validHex && !settings.wallpaperEnabled) { Text("应用") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("00696E", "6750A4", "386A20", "984061").forEach { hex ->
                    FilledTonalButton(onClick = feedbackClick { onChange(settings.copy(seed = 0xFF000000 or hex.toLong(16))) },
                        enabled = !settings.wallpaperEnabled, shapes = expressivePillShapes(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "主题色 #$hex" }, contentPadding = PaddingValues(0.dp)) {
                        Box(Modifier.size(22.dp).background(Color(0xFF000000 or hex.toLong(16)), MaterialTheme.shapes.small))
                    }
                }
            }
        }
        OutlinedButton(shapes = expressivePillShapes(), onClick = feedbackClick {
            val defaults = ThemeSettings()
            onChange(settings.copy(seed = defaults.seed, mode = defaults.mode, wallpaperEnabled = defaults.wallpaperEnabled))
        }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认主题") }
    }
}
