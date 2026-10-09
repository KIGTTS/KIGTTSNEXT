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
        SettingItem("对比度", "让文字更醒目", Icons.Default.Contrast, SettingPosition.FIRST) {
            SettingChoiceGroup("对比度",ContrastLevel.entries,settings.contrast,{ it.label },{ onChange(settings.copy(contrast=it)) })
        }
        ToggleSetting("更暗的深色模式", "深色模式下使用纯黑背景", Icons.Default.DarkMode,
            settings.deeperDark, { onChange(settings.copy(deeperDark = it)) }, position = SettingPosition.MIDDLE)
        SettingItem("圆角大小", "", Icons.Default.RoundedCorner, SettingPosition.LAST) {
            SettingChoiceGroup("圆角",CornerLevel.entries,settings.cornerLevel,{ it.label },{ onChange(settings.copy(cornerLevel=it)) })
        }
    }
    SettingsGroup("预览") {
        SettingItem("外观预览", "", Icons.Default.Preview, SettingPosition.SINGLE) {
            Text("这里是输入的文本", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = expressivePillShapes(), onClick = feedbackClick {}) { Text("朗读") }
                FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick {}) { Text("聆听") }
            }
        }
    }
}
