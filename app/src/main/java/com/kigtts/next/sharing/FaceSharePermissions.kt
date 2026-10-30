package com.kigtts.next.sharing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearbyError
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kigtts.next.ui.components.*

@Composable
internal fun rememberFacePermissionRequest(message: (String) -> Unit, notifications: Boolean = false): (Boolean, () -> Unit) -> Unit {
    val context = LocalContext.current
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissions by remember { mutableStateOf(emptyArray<String>()) }
    var nearby by remember { mutableStateOf(false) }; var prompt by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val pending = action; action = null
        if (result.values.all { it }) pending?.invoke()
        else if (nearby) {
            if (result.any { !it.value && it.key != Manifest.permission.POST_NOTIFICATIONS }) message("附近设备权限未开启，可以在同一 Wi-Fi 下查找")
            pending?.invoke()
        }
        else message("请允许附近设备权限后再创建热点")
    }
    if (prompt) MotionAlertDialog({ prompt = false; action = null }, title = { Text(if (nearby) "发现附近设备" else "创建临时热点") },
        icon = { Icon(Icons.Default.WifiTethering, null) }, text = {
            Text(if (nearby && notifications) "允许使用附近设备，以发现对方并传送照片。通知用于提示接收请求。"
                else if (nearby) "允许使用附近设备，以发现对方并传送照片。" else "允许使用附近设备，为对方创建用于领取照片的 Wi-Fi。")
        }, confirmButton = { TextButton(dialogFeedbackClick { prompt = false; launcher.launch(permissions) }) { Text("继续") } },
        dismissButton = { TextButton(dialogFeedbackClick { prompt = false; action = null }) { Text("取消") } })
    return { discover, next ->
        val required = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES) else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (discover && Build.VERSION.SDK_INT >= 31) addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
            if (notifications && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (required.isEmpty()) next()
        else { nearby = discover; permissions = required.toTypedArray(); action = next; prompt = true }
    }
}
