package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.cards.PhotoFactsConsent
import com.kigtts.next.ui.components.MotionAlertDialog

@Composable
internal fun PhotoFactsConsentHost() {
    val pending by PhotoFactsConsent.pending.collectAsState()
    pending?.let { request ->
        val weather = request.kind == "weather"
        MotionAlertDialog(onDismissRequest = { request.result.complete(false) },
            title = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (weather) "添加拍摄天气" else "添加拍摄地点")
                Icon(if (weather) Icons.Default.Cloud else Icons.Default.LocationOn, null)
            } },
            text = { Text(if (weather) {
                uiText("camera.weather", "service" to request.service)
            } else {
                uiText(if (request.service == "高德地图") "camera.location_provider" else "camera.location")
            }) },
            confirmButton = { TextButton({ request.result.complete(true) }) { Text(uiText("permission.allow")) } },
            dismissButton = { TextButton({ request.result.complete(false) }) { Text(uiText("photo.location_decline")) } })
    }
}
