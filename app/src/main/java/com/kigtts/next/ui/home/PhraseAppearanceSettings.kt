package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.MotionTextButton

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

@Composable
internal fun quickPhraseColor(phrase: QuickPhrase): Color = when (phrase.colorMode) {
    CardColorMode.DEFAULT, CardColorMode.NONE -> MaterialTheme.colorScheme.surfaceContainerLowest
    CardColorMode.CUSTOM -> Color(phrase.color)
}
@Composable
internal fun quickPhraseContentColor(phrase: QuickPhrase): Color = if (phrase.colorMode == CardColorMode.CUSTOM)
    if (Color(phrase.color).luminance() > .179f) Color.Black else Color.White else MaterialTheme.colorScheme.onSurface

@Composable
internal fun PhraseAppearanceSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    var picker by remember { mutableStateOf(false) }
    Text(uiText("quick.card_color"), style = MaterialTheme.typography.titleMedium)
    val default = uiText("appearance.contrast_choice.default")
    val custom = uiText("appearance.source.custom")
    ChoiceButtonGroup(uiText("quick.card_color"), listOf(CardColorMode.DEFAULT, CardColorMode.CUSTOM),
        if (draft.colorMode == CardColorMode.NONE) CardColorMode.DEFAULT else draft.colorMode,
        { if (it == CardColorMode.CUSTOM) custom else default },
        { if (enabled) { if (it == CardColorMode.CUSTOM) picker = true else model.content.editPhrase { current -> current.copy(colorMode = it) } } },
        Modifier.testTag("phrase-color-mode"),enabled=enabled)
    if (draft.colorMode == CardColorMode.CUSTOM) MotionTextButton(feedbackClick { picker = true }, enabled = enabled) {
        Box(Modifier.size(20.dp).background(Color(draft.color), MaterialTheme.shapes.small)); Spacer(Modifier.width(8.dp)); Text(uiText("quick.choose_color"))
    }
    if (picker) ColorPickerDialog(draft.color, { value -> model.content.editPhrase { it.copy(colorMode = CardColorMode.CUSTOM, color = value) }; picker = false },
        { picker = false }, title = uiText("quick.card_color"))
}
