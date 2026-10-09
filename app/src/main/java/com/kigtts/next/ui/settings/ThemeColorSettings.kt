@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
internal fun ThemeColorSettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    var colorInput by rememberSaveable(settings.seed) { mutableStateOf(settings.hex) }
    val validHex = colorInput.removePrefix("#").matches(Regex("[0-9a-fA-F]{6}"))
    val wallpaperAvailable = Build.VERSION.SDK_INT >= 31
    val customColorEnabled = !(settings.wallpaperEnabled && wallpaperAvailable)
    SettingsGroup("主题与配色") {
        SettingItem("主题模式", "", Icons.Default.BrightnessAuto, SettingPosition.FIRST) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(selected = settings.mode == mode, onClick = feedbackClick { onChange(settings.copy(mode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        modifier = Modifier.semantics { contentDescription = "主题模式：" + mode.label }) { Text(mode.label) }
                }
            }
        }
        ToggleSetting("壁纸取色", if (wallpaperAvailable) "使用壁纸颜色" else "需要 Android 12 及以上",
            Icons.Default.Wallpaper, settings.wallpaperEnabled && wallpaperAvailable, { onChange(settings.copy(wallpaperEnabled = it)) },
            position = SettingPosition.MIDDLE, enabled = wallpaperAvailable)
        SettingItem("主题色", if (customColorEnabled) "" else "关闭壁纸取色后可修改", Icons.Default.Palette, SettingPosition.LAST) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(colorInput, { colorInput = it }, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("颜色代码") }, isError = customColorEnabled && !validHex, enabled = customColorEnabled,
                    supportingText = { if (customColorEnabled && !validHex) Text("请输入 6 位颜色代码，如 #00696E") })
                FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick {
                    onChange(settings.copy(seed = 0xFF000000 or colorInput.removePrefix("#").toLong(16)))
                }, enabled = validHex && customColorEnabled) { Text("应用") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("00696E", "6750A4", "386A20", "984061").forEach { hex ->
                    FilledTonalButton(onClick = feedbackClick { onChange(settings.copy(seed = 0xFF000000 or hex.toLong(16))) },
                        enabled = customColorEnabled, shapes = expressivePillShapes(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "主题色 #$hex" }, contentPadding = PaddingValues(0.dp)) {
                        Box(Modifier.size(22.dp).background(Color(0xFF000000 or hex.toLong(16)), MaterialTheme.shapes.small))
                    }
                }
            }
        }
    }
}
