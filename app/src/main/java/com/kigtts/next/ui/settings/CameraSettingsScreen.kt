package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.cards.PhotoServiceSettings

@Composable internal fun CameraSettingsScreen(onWeather: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("polaroid_camera", 0) }
    var system by remember { mutableStateOf(preferences.getBoolean("use-system-camera", false)) }
    var full by remember { mutableStateOf(preferences.getBoolean("full-system-camera", true)) }
    val facts = remember(context) { PhotoServiceSettings(context) }
    var record by remember { mutableStateOf(facts.recordCapture) }
    SettingsLayout { SettingsGroup("相机") {
        ToggleSetting("默认使用系统相机", "编辑图片时优先使用系统相机。长按首页相机入口也可直接打开系统相机。", Icons.Default.PhotoCamera, system, {
            system = it; preferences.edit().putBoolean("use-system-camera", it).apply()
        }, position = SettingPosition.FIRST)
        ToggleSetting("完整系统相机", "使用系统相机的完整功能与镜头，返回后自动导入刚拍的照片。关闭后，拍完即可返回。", Icons.Default.PhotoCamera, full, {
            full = it; preferences.edit().putBoolean("full-system-camera", it).apply()
        }, position = SettingPosition.LAST)
    }
        SettingsGroup("拍摄信息") {
            ToggleSetting("拍照时记录位置与天气信息", "", Icons.Default.LocationOn, record, {
                record = it; facts.recordCapture = it
            }, position = SettingPosition.FIRST)
            SettingItem("天气设置", "位置与天气数据源", Icons.Default.Cloud, SettingPosition.LAST, onClick = onWeather,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
        }
    }
}
