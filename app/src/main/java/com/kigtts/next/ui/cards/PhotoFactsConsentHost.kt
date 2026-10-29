package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            text = { Text(if (weather) "允许将大致位置和拍摄日期发送给 Open-Meteo，查询拍摄时的天气？天气为估算值，免费服务限非商业用途。"
                else "用于给照片添加拍摄地点和天气。新拍照片使用当前位置，导入照片读取已有的拍摄位置。是否允许？") },
            confirmButton = { TextButton({ request.result.complete(true) }) { Text("允许") } },
            dismissButton = { TextButton({ request.result.complete(false) }) { Text("暂不允许") } })
    }
}
