@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.MotionOutlinedButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import com.kigtts.next.ui.components.animateEnabled
import com.kigtts.next.ui.components.enabledContentMotion

@Composable
internal fun PhraseDetailsForm(draft: PhraseDraft, categories: List<String>, model: AppViewModel, enabled: Boolean) {
    var title by rememberSaveable(draft.key) { mutableStateOf(draft.title) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val titleSource = remember { MutableInteractionSource() }
    val titleFocused by titleSource.collectIsFocusedAsState()
    LaunchedEffect(draft.title) { if (title != draft.title) title = draft.title }
    var chooseGroup by remember { mutableStateOf(false) }
    var appearance by rememberSaveable(draft.key) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if(!enabled)chooseGroup=false }
    Column(Modifier.fillMaxSize().testTag("phrase-details-form")
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PhraseEditorSection("字幕与标题") {
            RichPhraseTextField(draft, model, enabled)
            OutlinedTextField(title, { value -> title = value.replace('\n', ' '); model.content.editPhrase { it.copy(title = title) } },
                interactionSource = titleSource, colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled, titleFocused),
                enabled = enabled, label = { Text("缩略标题（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("phrase-detail-title"))
            Box {
                MotionOutlinedButton(feedbackClick { focus.clearFocus();keyboard?.hide();chooseGroup = true }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("phrase-detail-category")) { Text("分组：" + draft.category) }
                DropdownMenu(chooseGroup && enabled, { chooseGroup = false }) {
                    categories.forEach { group -> DropdownMenuItem(text = { Text(group) }, onClick = feedbackClick {
                        model.content.editPhrase { it.copy(category = group) }; chooseGroup = false
                    }) }
                }
            }
        }
        PhraseEditorSection("卡片内容") {
            PrimaryTabRow(draft.type.ordinal, Modifier.testTag("phrase-type-tabs")) {
                QuickCardType.entries.forEach { type -> Tab(draft.type == type,
                    feedbackClick { focus.clearFocus();keyboard?.hide();model.content.editPhrase { it.switchType(type) } }, enabled = enabled,
                    modifier = Modifier.testTag("phrase-type-" + type.name).enabledContentMotion(enabled), text = { Text(type.label) }) }
            }
            PhraseTypeContent(draft,model,enabled) { focus.clearFocus();keyboard?.hide() }
        }
        PhraseAppearanceSection(appearance,enabled,{ focus.clearFocus();keyboard?.hide();appearance=!appearance }) {
            PhraseAppearanceSettings(draft, model, enabled&&appearance)
        }
    }
}
