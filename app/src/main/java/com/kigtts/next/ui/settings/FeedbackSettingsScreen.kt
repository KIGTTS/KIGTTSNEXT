package com.kigtts.next.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration

@Composable
fun FeedbackSettingsScreen(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingsLayout {
        SettingsGroup("操作反馈") {
            ToggleSetting("按键震动反馈", "点击按钮、切换选项和长按时轻微震动。", Icons.Default.Vibration, enabled, onChange)
        }
        Text("遵循手机的系统振动设置。关闭后，点击与长按均不会震动。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
