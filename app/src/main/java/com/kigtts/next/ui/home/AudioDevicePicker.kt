package com.kigtts.next.ui.home

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.audio.AudioDeviceRouting
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun AudioDevicePicker(devices: AudioDeviceRouting, onDismiss: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val manager = LocalContext.current.getSystemService(AudioManager::class.java)
    DisposableEffect(manager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { revision++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { revision++ }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAudioDeviceCallback(callback) }
    }
    val endpoints = remember(revision) { devices.endpoints(false) }
    val selected = devices.selected(false)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("首选朗读设备") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                val rows = listOf(null to "系统默认") + endpoints.map { it.key to it.label }
                rows.forEach { (key, name) ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = feedbackClick {
                        runCatching { devices.select(false, key) }; onDismiss()
                    }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == key, onClick = null)
                        Text(name, Modifier.padding(start = 12.dp))
                    }
                }
                if (selected != null && endpoints.none { it.key == selected }) Text(devices.summary(false), Modifier.padding(12.dp))
            }
        }, confirmButton = { TextButton(onClick = feedbackClick(onDismiss)) { Text("完成") } })
}
