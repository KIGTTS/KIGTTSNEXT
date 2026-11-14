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

@Composable
internal fun UiLanguageScreen(locale: String, change: (UiLanguage) -> Unit) {
    SettingsLayout {
        SettingsGroup("界面语言", "") {
            UiLanguage.entries.forEachIndexed { index, language ->
                val active = language.tag == locale
                SettingItem(language.label, "", null, if (index == 0) SettingPosition.FIRST else SettingPosition.LAST,
                    Modifier.testTag("ui-language/${language.tag}").semantics { role = Role.RadioButton; selected = active },
                    onClick = { change(language) }, trailing = { RadioButton(selected = active, onClick = null) })
            }
        }
    }
}
