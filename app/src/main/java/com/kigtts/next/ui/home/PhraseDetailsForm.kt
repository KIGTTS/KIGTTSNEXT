@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange

@Composable
internal fun PhraseDetailsForm(draft: PhraseDraft, categories: List<String>, model: AppViewModel, enabled: Boolean) {
    var title by rememberSaveable(draft.key) { mutableStateOf(draft.title) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(draft.title) { if (title != draft.title) title = draft.title }
    var chooseGroup by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if(!enabled)chooseGroup=false }
        Column(Modifier.fillMaxSize().testTag("phrase-details-form")
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RichPhraseTextField(draft, model, enabled)
            OutlinedTextField(title, { value -> title = value.replace('\n', ' '); model.content.editPhrase { it.copy(title = title) } },
                enabled = enabled, label = { Text("缩略标题（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("phrase-detail-title"))
            PrimaryTabRow(draft.type.ordinal, Modifier.testTag("phrase-type-tabs")) {
                QuickCardType.entries.forEach { type -> Tab(draft.type == type,
                    feedbackClick { model.content.editPhrase { it.switchType(type) } }, enabled = enabled,
                    modifier = Modifier.testTag("phrase-type-" + type.name), text = { Text(type.label) }) }
            }
            if (draft.type == QuickCardType.TEXT) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("富文本")
                Switch(draft.richText, feedbackChange { value -> model.content.editPhrase { it.copy(richText = value) } },
                    enabled = enabled, modifier = Modifier.testTag("phrase-rich-text"))
            }
            PhraseResourceSettings(draft, model, enabled) { focus.clearFocus(); keyboard?.hide() }
            PhraseAppearanceSettings(draft, model, enabled)
            Box {
                OutlinedButton(feedbackClick { chooseGroup = true }, enabled = enabled, modifier = Modifier.testTag("phrase-detail-category")) {
                    Text("分组：" + draft.category)
                }
                DropdownMenu(chooseGroup && enabled, { chooseGroup = false }) {
                    categories.forEach { group -> DropdownMenuItem(text = { Text(group) }, onClick = feedbackClick {
                        model.content.editPhrase { it.copy(category = group) }; chooseGroup = false
                    }) }
                }
            }
        }
}
