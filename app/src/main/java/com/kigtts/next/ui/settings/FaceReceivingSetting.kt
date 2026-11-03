package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.sharing.rememberFacePermissionRequest
import com.kigtts.next.state.AppViewModel

@Composable internal fun FaceReceivingSetting(model: AppViewModel, position: SettingPosition) {
    val enabled by model.faceShare.receiving.collectAsStateWithLifecycle()
    val permission = rememberFacePermissionRequest(model::message, notifications = true)
    ToggleSetting("面对面接收", "开启后可被附近的人发现", Icons.Default.MoveToInbox, enabled, { next ->
        if (next) permission(true) { model.faceShare.setReceiving(true) }
        else model.faceShare.setReceiving(false)
    }, position = position)
}
