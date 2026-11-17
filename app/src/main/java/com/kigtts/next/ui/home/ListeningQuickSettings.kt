package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.SettingsGroup

@Composable
internal fun CaptionWindowSetting(open: () -> Unit) {
    SettingItem("打开字幕浮窗", "可先打开浮窗，再开始聆听",
        MaterialSymbols.vector("open_in_new") ?: Icons.Default.OpenInNew, SettingPosition.SINGLE, onClick = open)
}

@Composable
internal fun ListeningQuickSettings(model: AppViewModel, size: Float, dismiss: () -> Unit) {
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text("聆听字幕") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup("字幕字号") {
                Slider(size, model::setCaptionTextSize, Modifier.fillMaxWidth(), valueRange = 10f..64f)
            }
            CaptionWindowSetting { dismiss(); model.openCaptionWindow() }
            SettingItem("语音识别设置", "", MaterialSymbols.vector("tune") ?: Icons.Default.Tune,
                SettingPosition.SINGLE, onClick = { dismiss(); model.navigate(Destination.PluginPage("service.speech-recognition", "manager")) })
        }
    }, confirmButton = { TextButton(dialogFeedbackClick(dismiss)) { Text("完成") } })
}
