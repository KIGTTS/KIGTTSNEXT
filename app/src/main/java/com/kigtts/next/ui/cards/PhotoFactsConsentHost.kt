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
            text = { Text(if (weather) "允许把约略经纬度和拍摄日期发送给 Open-Meteo，取得拍摄时段的模型天气估计并固定保存在照片中？免费接口仅限非商业用途，天气数据由 Open-Meteo 提供。原始照片不会上传。"
                else "允许为新拍摄的照片读取大致位置，并通过系统地理编码服务转换地点名称？地点在拍摄时固定保存。旧照片仅使用原图已有的位置信息，不会套用你现在的位置。") },
            confirmButton = { TextButton({ request.result.complete(true) }) { Text("允许") } },
            dismissButton = { TextButton({ request.result.complete(false) }) { Text("暂不允许") } })
    }
}
