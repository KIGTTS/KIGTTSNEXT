package com.kigtts.next.sharing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.delay

@Composable
internal fun FaceDiscoveryHelp(refresh: () -> Unit) {
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(8_000); waiting = true }
    if (waiting) {
        Text("让对方打开分享或面对面接收，并连接同一 Wi-Fi。仍找不到时，可以使用“Wi-Fi 互传”扫码领取。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(feedbackClick(refresh)) {
            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("重新查找")
        }
    }
}
