package com.kigtts.next.ui.settings

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.i18n.uiText

@Composable
internal fun UiLanguageScreen(selection: UiLanguage, change: (UiLanguage) -> Unit) {
    SettingsLayout {
        SettingsGroup(uiText("language.screen"), uiText("language.scope")) {
            UiLanguage.entries.forEachIndexed { index, language ->
                val active = language == selection
                val position = when (index) {
                    0 -> SettingPosition.FIRST
                    UiLanguage.entries.lastIndex -> SettingPosition.LAST
                    else -> SettingPosition.MIDDLE
                }
                SettingItem(uiLanguageLabel(language), "", null, position,
                    Modifier.testTag("ui-language/${language.tag}").semantics { role = Role.RadioButton; selected = active },
                    onClick = { change(language) }, trailing = { RadioButton(selected = active, onClick = null) })
            }
        }
    }
}

@Composable internal fun uiLanguageLabel(language: UiLanguage) =
    if (language == UiLanguage.SYSTEM) uiText("language.system") else language.label
