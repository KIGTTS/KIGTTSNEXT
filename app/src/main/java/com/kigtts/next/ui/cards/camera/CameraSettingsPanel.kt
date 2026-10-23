package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*

@Composable
internal fun CameraSettingsPanel(options: CameraOptions, flashAvailable: Boolean, enabled: Boolean = true,
    availableProcessing: Set<CameraProcessing> = setOf(CameraProcessing.STANDARD), manual: CameraManualController? = null) {
    if (!options.settingsVisible) return
    MotionAlertDialog(onDismissRequest = options::hideSettings, title = { Text("相机设置") },
        confirmButton = {
            val finish = rememberDialogClose()
            MotionTextButton(feedbackClick { finish(options::hideSettings) }) { Text("完成") }
        }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroup("构图") {
                    SettingItem("辅助线", "", Icons.Default.Grid3x3, SettingPosition.FIRST) {
                        ChoiceButtonGroup("辅助线", CameraGrid.entries, options.grid, { it.label }, options::setGrid,
                            enabled = enabled, compactLabels = true)
                    }
                    ToggleSetting("水平仪", "帮助保持画面水平", Icons.Default.Straighten, options.level,
                        options::setLevel, position = SettingPosition.LAST, enabled = enabled)
                }
                SettingsGroup("拍摄") {
                    SettingItem("照片处理", "", Icons.Default.AutoAwesome, SettingPosition.FIRST) {
                        val choices = CameraProcessing.entries.filter { it in availableProcessing }
                        ChoiceButtonGroup("照片处理", choices, options.processing.takeIf { it in choices } ?: CameraProcessing.STANDARD,
                            { it.label }, options::setProcessing, enabled = enabled, compactLabels = true)
                    }
                    SettingItem("闪光灯", if (flashAvailable) "" else "当前镜头不支持闪光灯",
                        Icons.Default.FlashOn, SettingPosition.MIDDLE) {
                        ChoiceButtonGroup("闪光灯", CameraFlash.entries, options.flash, { it.label }, options::setFlash,
                            enabled = enabled && flashAvailable, compactLabels = true)
                    }
                    SettingItem("倒计时", "", Icons.Default.Timer, SettingPosition.LAST) {
                        ChoiceButtonGroup("倒计时", CameraOptions.TIMERS, options.timerSeconds,
                            { if (it == 0) "关闭" else "$it 秒" }, options::setTimer,
                            enabled = enabled, compactLabels = true)
                    }
                }
                manual?.let { CameraManualPanel(it, enabled) }
            }
        })
}
