package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import com.kigtts.next.theme.*

@Composable
internal fun AppearanceDisplaySettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    SettingsGroup(uiText("appearance.display_shapes")) {
        SettingItem(uiText("appearance.contrast"), uiText("appearance.contrast.hint"), Icons.Default.Contrast, SettingPosition.FIRST) {
            SettingChoiceGroup(uiText("appearance.contrast"),ContrastLevel.entries,settings.contrast,{ it.label },{ onChange(settings.copy(contrast=it)) })
        }
        ToggleSetting(uiText("appearance.dark"), uiText("appearance.dark.body"), Icons.Default.DarkMode,
            settings.deeperDark, { onChange(settings.copy(deeperDark = it)) }, position = SettingPosition.MIDDLE)
        SettingItem(uiText("appearance.corners"), "", Icons.Default.RoundedCorner, SettingPosition.LAST) {
            SettingChoiceGroup(uiText("appearance.corner"),CornerLevel.entries,settings.cornerLevel,{ it.label },{ onChange(settings.copy(cornerLevel=it)) })
        }
    }
}
