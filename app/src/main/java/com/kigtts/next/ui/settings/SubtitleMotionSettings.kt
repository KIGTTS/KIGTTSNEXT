package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.runtime.Composable
import com.kigtts.next.state.SubtitleAppearance

@Composable
internal fun SubtitleMotionSettings(value: SubtitleAppearance, onChange: (SubtitleAppearance) -> Unit) {
    SettingsGroup("字幕动效") {
        ToggleSetting(uiText("appearance.less_motion"), uiText("appearance.less_motion.body"), Icons.Default.Animation,
            value.reducedMotion, { onChange(value.copy(reducedMotion = it)) }, position = SettingPosition.FIRST)
        ToggleSetting(uiText("appearance.reflow"), uiText("appearance.reflow.body"), Icons.AutoMirrored.Filled.WrapText,
            value.displayReflow, { onChange(value.copy(displayReflow = it)) },
            position = SettingPosition.LAST, enabled = !value.reducedMotion)
    }
}
