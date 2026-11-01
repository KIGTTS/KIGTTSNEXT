package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.sharing.FaceConnectionDialog
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.sharing.rememberFacePermissionRequest
import com.kigtts.next.state.AppViewModel

@Composable internal fun FaceReceivingSetting(model: AppViewModel, position: SettingPosition) {
    val enabled by model.faceShare.receiving.collectAsStateWithLifecycle()
    val permission = rememberFacePermissionRequest(model::message, notifications = true)
    var connection by remember { mutableStateOf(false) }
    if (connection) FaceConnectionDialog(model.faceShare) { connection = false }
    Column {
    ToggleSetting("面对面接收", "开启后可被附近的人发现", Icons.Default.MoveToInbox, enabled, { next ->
        if (next) permission(true) { model.faceShare.setReceiving(true) }
        else model.faceShare.setReceiving(false)
    }, position = if (enabled) {
        if (position == SettingPosition.SINGLE || position == SettingPosition.FIRST) SettingPosition.FIRST else SettingPosition.MIDDLE
    } else position)
    if (enabled) SettingItem("连接设备", "同一 Wi-Fi、附近直连或临时热点", Icons.Default.WifiTethering,
        if (position == SettingPosition.SINGLE || position == SettingPosition.LAST) SettingPosition.LAST else SettingPosition.MIDDLE,
        onClick = { connection = true })
    }
}
