package com.kigtts.next.ui.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun PhotoWeatherSettingsScreen() {
    val context = LocalContext.current
    val preferences = remember(context) { PhotoServiceSettings(context) }
    var weather by remember { mutableStateOf(preferences.weatherSource) }
    var place by remember { mutableStateOf(preferences.placeSource) }
    var manual by remember { mutableStateOf(preferences.manual) }
    var location by remember { mutableStateOf(preferences.manualLocation) }
    var editingLocation by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<PhotoWeatherSource?>(null) }
    var keyRevision by remember { mutableIntStateOf(0) }
    if (editingLocation) PhotoManualLocationDialog(location, { editingLocation = false }) { value ->
        preferences.saveLocation(value.name, value.latitude, value.longitude)
        location = value; manual = true; editingLocation = false
    }
    editingKey?.let { source ->
        PhotoWeatherKeyDialog(source, preferences.key(source), { editingKey = null }) { value ->
            preferences.saveKey(source, value); keyRevision++; editingKey = null
        }
    }
    SettingsLayout {
        SettingsGroup("位置") {
            SettingItem("位置来源", "自动时使用照片位置或手机定位", Icons.Default.LocationOn, SettingPosition.FIRST) {
                SettingChoiceGroup("位置来源", listOf(false, true), manual, { if (it) "手动" else "自动" }, { selected ->
                    if (selected && location == null) editingLocation = true
                    else { preferences.manual = selected; manual = selected }
                })
            }
            SettingItem("手动位置", location?.let { "${it.name.ifBlank { "自定义位置" }} · ${it.latitude}, ${it.longitude}" } ?: "未设置",
                Icons.Default.EditLocationAlt, SettingPosition.MIDDLE, onClick = { editingLocation = true })
            SettingItem("地名数据源", "", Icons.Default.Map, SettingPosition.LAST) {
                SettingChoiceGroup("地名数据源", PhotoPlaceSource.entries, place, { it.label }, {
                    preferences.placeSource = it; place = it
                })
            }
        }
        SettingsGroup("天气数据源") {
            PhotoWeatherSource.entries.forEachIndexed { index, source ->
                val selected = source == weather
                SettingItem(source.label, source.description, Icons.Default.Cloud,
                    if (index == 0) SettingPosition.FIRST else if (index == PhotoWeatherSource.entries.lastIndex) SettingPosition.LAST else SettingPosition.MIDDLE,
                    Modifier.selectable(selected, role = Role.RadioButton, onClick = feedbackClick {
                        if (selected && source != PhotoWeatherSource.OPEN_METEO) editingKey = source
                        else { preferences.weatherSource = source; weather = source }
                    }), trailing = { RadioButton(selected, null) })
            }
        }
        SettingsGroup("服务密钥") {
            listOf(PhotoWeatherSource.AMAP, PhotoWeatherSource.SENIVERSE).forEachIndexed { index, source ->
                val configured = remember(source, keyRevision) { preferences.key(source).isNotBlank() }
                SettingItem(photoWeatherKeyLabel(source), if (configured) "已设置" else "未设置", Icons.Default.Key,
                    if (index == 0) SettingPosition.FIRST else SettingPosition.LAST, onClick = { editingKey = source })
            }
        }
    }
}
