@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.components.*

@Composable
fun AppearanceSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit,
    placeholder: String = "点击输入文字", onPlaceholderChange: (String) -> Unit = {},
    subtitle: com.kigtts.next.state.SubtitleAppearance = com.kigtts.next.state.SubtitleAppearance(),
    onSubtitleChange: (com.kigtts.next.state.SubtitleAppearance) -> Unit = {}) {
    SettingsLayout {
        ThemeColorSettings(settings, onChange)
        AppearanceDisplaySettings(settings, onChange)
        SubtitlePlaceholderSetting(placeholder, onPlaceholderChange)
        SubtitleMotionSettings(subtitle, onSubtitleChange)
        OutlinedButton(shapes = expressivePillShapes(), onClick = feedbackClick { onChange(ThemeSettings()) },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restore, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("恢复默认外观")
        }
    }
}
