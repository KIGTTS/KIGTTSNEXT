package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable internal fun CameraSettingsScreen() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("polaroid_camera", 0) }
    var system by remember { mutableStateOf(preferences.getBoolean("use-system-camera", false)) }
    var full by remember { mutableStateOf(preferences.getBoolean("full-system-camera", true)) }
    SettingsLayout { SettingsGroup("相机") {
        ToggleSetting("默认使用系统相机", "关闭时使用内建相机；长按拍照或选图按钮始终可以打开系统相机。", Icons.Default.PhotoCamera, system, {
            system = it; preferences.edit().putBoolean("use-system-camera", it).apply()
        })
        ToggleSetting("完整系统相机", "使用系统相机完整界面与可用镜头；拍摄后返回，选择刚拍摄的照片。关闭后使用直接回传模式。", Icons.Default.PhotoCamera, full, {
            full = it; preferences.edit().putBoolean("full-system-camera", it).apply()
        })
    }
        SettingsGroup("拍摄信息") {
            Text("日期、时间、地点和天气固定保存在照片中，不随之后的位置和天气变化。地点与天气首次使用分别申请授权。")
            TextButton({ context.getSharedPreferences("polaroid-facts", 0).edit().clear().apply() }) { Text("清除地点与天气授权") }
        }
    }
}
