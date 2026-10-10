package com.kigtts.next.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.SettingChoiceGroup

@Composable
internal fun quickPhraseColor(phrase: QuickPhrase): Color = when (phrase.colorMode) {
    CardColorMode.DEFAULT -> MaterialTheme.colorScheme.surfaceContainerLowest
    CardColorMode.CUSTOM -> Color(phrase.color)
    CardColorMode.NONE -> Color.Transparent
}
@Composable
internal fun quickPhraseContentColor(phrase: QuickPhrase): Color = if (phrase.colorMode == CardColorMode.CUSTOM)
    if (Color(phrase.color).luminance() > .179f) Color.Black else Color.White else MaterialTheme.colorScheme.onSurface

@Composable
internal fun PhraseAppearanceSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    var picker by remember { mutableStateOf(false) }
    Text("卡片颜色", style = MaterialTheme.typography.titleMedium)
    SettingChoiceGroup("卡片颜色", CardColorMode.entries, draft.colorMode,
        { when (it) { CardColorMode.DEFAULT -> "默认"; CardColorMode.CUSTOM -> "自定义"; CardColorMode.NONE -> "无颜色" } },
        { if (enabled) { if (it == CardColorMode.CUSTOM) picker = true else model.content.editPhrase { current -> current.copy(colorMode = it) } } },
        Modifier.testTag("phrase-color-mode"))
    if (draft.colorMode == CardColorMode.CUSTOM) TextButton(feedbackClick { picker = true }, enabled = enabled) {
        Box(Modifier.size(20.dp).background(Color(draft.color), MaterialTheme.shapes.small)); Spacer(Modifier.width(8.dp)); Text("选择颜色")
    }
    if (picker) ColorPickerDialog(draft.color, { value -> model.content.editPhrase { it.copy(colorMode = CardColorMode.CUSTOM, color = value) }; picker = false },
        { picker = false }, title = "卡片颜色")
}
