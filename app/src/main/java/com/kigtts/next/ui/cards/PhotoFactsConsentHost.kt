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
            text = { Text(if (weather) "允许将大致位置和拍摄日期发送给 Open-Meteo，查询并记录拍摄时的天气？天气为估算结果，仅供参考。免费服务仅限非商业用途，不会发送照片。"
                else "允许获取新照片的拍摄位置，并通过系统服务查询地点名称？地点会随照片保存。导入旧照片时，只使用原图记录的位置。") },
            confirmButton = { TextButton({ request.result.complete(true) }) { Text("允许") } },
            dismissButton = { TextButton({ request.result.complete(false) }) { Text("暂不允许") } })
    }
}
