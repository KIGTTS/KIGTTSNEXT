package com.kigtts.next.ui.home

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.audio.AudioDeviceRouting
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*
import com.kigtts.next.ui.plugins.SpeechGainSlider
import com.kigtts.plugin.api.PluginControl

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
    var error by remember { mutableStateOf<String?>(null) }
    MotionAlertDialog(onDismissRequest = onDismiss, title = { Text("音频设备") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(true, false).forEach { input ->
                    val side = if (input) "input" else "output"
                    val endpoints = remember(input, revision) { devices.endpoints(input) }
                    var expanded by remember(input) { mutableStateOf(false) }
                    Box {
                        SettingItem(if (input) "音频输入设备" else "音频输出设备", devices.summary(input),
                            if (input) Icons.Default.Mic else Icons.Default.Speaker,
                            if (input) SettingPosition.FIRST else SettingPosition.MIDDLE,
                            Modifier.testTag("audio-device-menu/$side"), onClick = { expanded = true },
                            trailing = { Icon(Icons.Default.ArrowDropDown, null) })
                        DropdownMenu(expanded, { expanded = false }) {
                            (listOf(null to "系统默认") + endpoints.map { it.key to it.label }).forEach { (key, name) ->
                                DropdownMenuItem(text = { Text(name) }, onClick = feedbackClick {
                                    expanded = false
                                    runCatching { devices.select(input, key) }.onSuccess { error = null }
                                        .onFailure { error = it.message ?: "设备未能切换，请重试" }
                                    revision++
                                }, modifier = Modifier.testTag("audio-device-option/$side/${key ?: "default"}"),
                                    trailingIcon = { if (devices.selected(input) == key) Icon(Icons.Default.Check, "已选用") })
                            }
                        }
                    }
                }
                Surface(shape = groupedSettingShape(SettingPosition.LAST, MaterialTheme.shapes.small),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    SpeechGainSlider(PluginControl.Slider("语音合成音量", "gain", devices.outputGain, 0f, 10f),
                        Modifier.padding(16.dp), enabled = true, continuous = false) { value ->
                        devices.setOutputGain(value.toFloat()); revision++
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(onClick = feedbackClick(onDismiss)) { Text("完成") } })
}
