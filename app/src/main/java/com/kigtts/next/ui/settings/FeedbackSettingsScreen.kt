package com.kigtts.next.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun FeedbackSettingsScreen(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingsLayout {
        SettingsCard {
            ToggleSetting("按键震动反馈", "按钮、菜单、切换与插件入口共用触感反馈", enabled, onChange)
        }
        Text("使用系统触感反馈，遵循设备的触感设置。关闭后也会停止长按反馈。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
