package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.PhotoManualLocation
import com.kigtts.next.cards.PhotoWeatherSource
import com.kigtts.next.cards.validPhotoCoordinates
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick

@Composable internal fun photoWeatherKeyLabel(source: PhotoWeatherSource) =
    if (source == PhotoWeatherSource.AMAP) uiText("camera.weather.key.amap") else uiText("camera.weather.key.seniverse")

@Composable internal fun PhotoWeatherKeyDialog(source: PhotoWeatherSource, current: String, close: () -> Unit, save: (String) -> Unit) {
    var key by remember(source) { mutableStateOf(current) }
    MotionAlertDialog(close, title = { Text(photoWeatherKeyLabel(source)) }, text = {
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
    }, confirmButton = { TextButton(dialogFeedbackClick { save(key) }) { Text(uiText("action.save")) } },
        dismissButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}

@Composable internal fun PhotoManualLocationDialog(current: PhotoManualLocation?, close: () -> Unit, save: (PhotoManualLocation) -> Unit) {
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    var latitude by remember { mutableStateOf(current?.latitude?.toString().orEmpty()) }
    var longitude by remember { mutableStateOf(current?.longitude?.toString().orEmpty()) }
    val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
    val valid = validPhotoCoordinates(lat, lon)
    MotionAlertDialog(close, title = { Text(uiText("camera.weather.manual_place")) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(), label = { Text(uiText("camera.weather.place_name")) }, singleLine = true)
            OutlinedTextField(latitude, { latitude = it }, Modifier.fillMaxWidth(), label = { Text(uiText("camera.weather.latitude")) },
                singleLine = true, isError = latitude.isNotEmpty() && (lat == null || !lat.isFinite() || lat !in -90.0..90.0),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(longitude, { longitude = it }, Modifier.fillMaxWidth(), label = { Text(uiText("camera.weather.longitude")) },
                singleLine = true, isError = longitude.isNotEmpty() && (lon == null || !lon.isFinite() || lon !in -180.0..180.0),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }, confirmButton = { TextButton(dialogFeedbackClick { save(PhotoManualLocation(name, lat!!, lon!!)) }, enabled = valid) { Text(uiText("action.save")) } },
        dismissButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}
