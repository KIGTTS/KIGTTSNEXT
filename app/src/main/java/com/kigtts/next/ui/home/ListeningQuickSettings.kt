package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.theme.AppearanceCardSurface
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.components.LocalCornerRadius
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.SettingsGroup

@Composable
internal fun CaptionWindowSetting(ready: Boolean = true, open: () -> Unit) {
    val inset = 8.dp
    val innerRadius = ((LocalCornerRadius.current + 4.dp).coerceAtMost(24.dp) - inset).coerceAtLeast(0.dp)
    AppearanceCardSurface(Modifier.fillMaxWidth()) {
        SettingItem(uiText("listening.open_window"), uiText("listening.window_hint"),
            MaterialSymbols.vector("open_in_new") ?: Icons.AutoMirrored.Filled.OpenInNew, SettingPosition.SINGLE,
            modifier = Modifier.padding(inset), onClick = open, enabled = ready, corners = RoundedCornerShape(innerRadius))
    }
}

@Composable
internal fun ListeningQuickSettings(model: AppViewModel, size: Float, dismiss: () -> Unit) {
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(uiText("listening.panel")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup(uiText("listening.text_size")) {
                Slider(size, model::setCaptionTextSize, Modifier.fillMaxWidth(), valueRange = 10f..64f)
            }
            CaptionWindowSetting { dismiss(); model.openCaptionWindow() }
            SettingItem(uiText("listening.settings"), "", MaterialSymbols.vector("tune") ?: Icons.Default.Tune,
                SettingPosition.SINGLE, onClick = { dismiss(); model.navigate(Destination.PluginPage("service.speech-recognition", "manager")) })
        }
    }, confirmButton = { TextButton(dialogFeedbackClick(dismiss)) { Text(uiText("action.done")) } })
}
