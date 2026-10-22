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
        ToggleSetting("默认使用系统相机", "关闭后使用内置相机。长按拍照或选图按钮也能打开系统相机。", Icons.Default.PhotoCamera, system, {
            system = it; preferences.edit().putBoolean("use-system-camera", it).apply()
        })
        ToggleSetting("完整系统相机", "使用系统相机的完整功能与镜头，返回后自动导入刚拍的照片。关闭后，拍完即可返回。", Icons.Default.PhotoCamera, full, {
            full = it; preferences.edit().putBoolean("full-system-camera", it).apply()
        })
    }
        SettingsGroup("拍摄信息") {
            Text("记录拍摄时的日期、时间、地点和天气。首次添加地点或天气时会询问授权。")
            TextButton({ context.getSharedPreferences("polaroid-facts", 0).edit().clear().apply() }) { Text("清除地点与天气授权") }
        }
    }
}
