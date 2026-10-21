package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable internal fun CameraSettingsScreen() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("polaroid_camera", 0) }
    var system by remember { mutableStateOf(preferences.getBoolean("use-system-camera", false)) }
    SettingsLayout { SettingsGroup("相机") {
        ToggleSetting("默认使用系统相机", "关闭时使用内建相机；长按拍照或选图按钮始终可以打开系统相机。", Icons.Default.PhotoCamera, system, {
            system = it; preferences.edit().putBoolean("use-system-camera", it).apply()
        })
    } }
}
