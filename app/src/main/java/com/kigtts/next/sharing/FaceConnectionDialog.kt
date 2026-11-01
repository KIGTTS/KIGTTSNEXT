package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.ui.components.*

/** The transport belongs to the facing session, independently of browser collection. */
@Composable internal fun FaceConnectionDialog(controller: FaceShareController, close: () -> Unit) {
    val hotspot by controller.hotspot.details.collectAsStateWithLifecycle()
    val starting by controller.hotspot.starting.collectAsStateWithLifecycle()
    val permission = rememberFacePermissionRequest({ controller.connectionMessage(it) })
    MotionAlertDialog(close, icon = { Icon(Icons.Default.WifiTethering, null) }, title = { Text("连接设备") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hotspot != null) {
                FaceQrCode(hotspot!!.qr(), "连接热点二维码", 200.dp)
                Text(hotspot!!.ssid, style = MaterialTheme.typography.titleMedium)
                Text("密码：${hotspot!!.password}")
                Text("让对方扫码连接后，直接在分享栏点头像发送。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(feedbackClick(controller.hotspot::stop)) { Text("关闭热点") }
            } else {
                Text("同一 Wi-Fi 下可直接互传。没有 Wi-Fi 时，可尝试附近直连，或创建热点让对方连接。")
                FilledTonalButton(feedbackClick { permission(false, controller::startHotspot) }, enabled = !starting) {
                    if (starting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (starting) "正在创建热点" else "创建临时热点")
                }
            }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick(close)) { Text("完成") } })
}
