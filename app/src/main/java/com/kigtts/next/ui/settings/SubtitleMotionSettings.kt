package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.runtime.Composable
import com.kigtts.next.state.SubtitleAppearance

@Composable
internal fun SubtitleMotionSettings(value: SubtitleAppearance, onChange: (SubtitleAppearance) -> Unit) {
    SettingsGroup("字幕动效") {
        ToggleSetting("减弱字幕动效", "只保留清屏、快捷文本上屏和占位提示的淡入淡出", Icons.Default.Animation,
            value.reducedMotion, { onChange(value.copy(reducedMotion = it)) }, position = SettingPosition.FIRST)
        ToggleSetting("展示时换行动效", "调整字号或面板大小时，文字平滑换行", Icons.AutoMirrored.Filled.WrapText,
            value.displayReflow, { onChange(value.copy(displayReflow = it)) },
            position = SettingPosition.LAST, enabled = !value.reducedMotion)
    }
}
