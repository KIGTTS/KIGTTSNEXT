package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import java.util.Locale
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
    val autoLabel = uiText("option.auto")
    val manualLabel = uiText("option.manual")
    val customPlace = uiText("camera.weather.custom_place")
    val placeNames = PhotoPlaceSource.entries.associateWith { uiText("camera.weather.place.${it.name.lowercase(Locale.ROOT)}") }
    SettingsLayout {
        SettingsGroup(uiText("camera.weather.place")) {
            SettingItem(uiText("camera.weather.place_source"), uiText("camera.weather.auto_location"), Icons.Default.LocationOn, SettingPosition.FIRST) {
                SettingChoiceGroup(uiText("camera.weather.place_source"), listOf(false, true), manual, { if (it) manualLabel else autoLabel }, { selected ->
                    if (selected && location == null) editingLocation = true
                    else { preferences.manual = selected; manual = selected }
                })
            }
            SettingItem(uiText("camera.weather.manual_place"), location?.let { "${it.name.ifBlank { customPlace }} · ${it.latitude}, ${it.longitude}" } ?: uiText("camera.weather.unset"),
                Icons.Default.EditLocationAlt, SettingPosition.MIDDLE, onClick = { editingLocation = true })
            SettingItem(uiText("camera.weather.place_name_source"), "", Icons.Default.Map, SettingPosition.LAST) {
                SettingChoiceGroup(uiText("camera.weather.place_name_source"), PhotoPlaceSource.entries, place, { placeNames.getValue(it) }, {
                    preferences.placeSource = it; place = it
                })
            }
        }
        SettingsGroup(uiText("camera.weather.source")) {
            PhotoWeatherSource.entries.forEachIndexed { index, source ->
                val selected = source == weather
                SettingItem(uiText("camera.weather.source.${source.name.lowercase(Locale.ROOT)}"),
                    uiText("camera.weather.detail.${source.name.lowercase(Locale.ROOT)}"), Icons.Default.Cloud,
                    if (index == 0) SettingPosition.FIRST else if (index == PhotoWeatherSource.entries.lastIndex) SettingPosition.LAST else SettingPosition.MIDDLE,
                    Modifier.selectable(selected, role = Role.RadioButton, onClick = feedbackClick {
                        if (selected && source.requiresKey) editingKey = source
                        else { preferences.weatherSource = source; weather = source }
                    }), trailing = { RadioButton(selected, null) })
            }
        }
        SettingsGroup(uiText("camera.weather.keys")) {
            listOf(PhotoWeatherSource.AMAP, PhotoWeatherSource.SENIVERSE).forEachIndexed { index, source ->
                val configured = remember(source, keyRevision) { preferences.key(source).isNotBlank() }
                SettingItem(photoWeatherKeyLabel(source), if (configured) uiText("camera.weather.set") else uiText("camera.weather.unset"), Icons.Default.Key,
                    if (index == 0) SettingPosition.FIRST else SettingPosition.LAST, onClick = { editingKey = source })
            }
        }
    }
}
