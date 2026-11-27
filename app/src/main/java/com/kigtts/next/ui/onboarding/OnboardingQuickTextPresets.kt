package com.kigtts.next.ui.onboarding

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.QuickTextPreset
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.SettingsGroup

@Composable
internal fun OnboardingQuickTextPresets(
    presets: List<QuickTextPreset>, selected: Set<String>, toggle: (String) -> Unit,
) {
    SettingsGroup(uiText("onboarding.presets.groups")) {
        presets.forEachIndexed { index, preset ->
            val checked = preset.id in selected
            val position = when (index) {
                0 -> SettingPosition.FIRST
                presets.lastIndex -> SettingPosition.LAST
                else -> SettingPosition.MIDDLE
            }
            SettingItem(preset.name, preset.phrases.take(3).joinToString(" · ") { it.title }, null, position,
                modifier = Modifier.testTag("onboarding-preset/${preset.id}"),
                interaction = Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = feedbackChange { toggle(preset.id) }),
                trailing = { Checkbox(checked, onCheckedChange = null) })
        }
    }
}
