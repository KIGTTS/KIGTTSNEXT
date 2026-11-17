package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.cards.PhotoServiceSettings
import com.kigtts.next.cards.AndroidPhotoFactsAccess
import kotlinx.coroutines.launch

@Composable internal fun CameraSettingsScreen(onWeather: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("polaroid_camera", 0) }
    var system by remember { mutableStateOf(preferences.getBoolean("use-system-camera", false)) }
    var full by remember { mutableStateOf(preferences.getBoolean("full-system-camera", true)) }
    val facts = remember(context) { PhotoServiceSettings(context) }
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf(facts.recordCapture) }
    SettingsLayout { SettingsGroup(uiText("quick.camera")) {
        ToggleSetting(uiText("camera.system_default"), uiText("camera.system_default_hint"), Icons.Default.PhotoCamera, system, {
            system = it; preferences.edit().putBoolean("use-system-camera", it).apply()
        }, position = SettingPosition.FIRST)
        ToggleSetting(uiText("camera.full_system.title"), uiText("camera.full_system.body"), Icons.Default.PhotoCamera, full, {
            full = it; preferences.edit().putBoolean("full-system-camera", it).apply()
        }, position = SettingPosition.LAST)
    }
        SettingsGroup(uiText("camera.capture_details")) {
            ToggleSetting(uiText("camera.record_facts"), "", Icons.Default.LocationOn, record, {
                record = it; facts.recordCapture = it
                if (it) scope.launch {
                    val access = AndroidPhotoFactsAccess(context)
                    val hasLocation = if (facts.manual && facts.manualLocation != null) access.consent("location") else access.locationPermission()
                    if (hasLocation && facts.recordCapture) access.consent("weather")
                }
            }, position = SettingPosition.FIRST)
            SettingItem(uiText("camera.weather_settings"), uiText("camera.weather_source_hint"), Icons.Default.Cloud, SettingPosition.LAST, onClick = onWeather,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
        }
    }
}
