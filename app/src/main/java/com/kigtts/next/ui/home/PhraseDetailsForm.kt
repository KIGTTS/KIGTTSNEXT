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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.ui.components.textMenuAnchor
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.animateEnabled

@Composable
internal fun PhraseDetailsForm(draft: PhraseDraft, categories: List<String>, model: AppViewModel, enabled: Boolean) {
    var title by rememberSaveable(draft.key,stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.title)) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val titleSource = remember { MutableInteractionSource() }
    val titleFocused by titleSource.collectIsFocusedAsState()
    com.kigtts.next.ui.navigation.RegisterPageInput(titleFocused)
    LaunchedEffect(draft.title) { if (title.text != draft.title) title = TextFieldValue(draft.title) }
    var chooseGroup by remember { mutableStateOf(false) }
    var appearance by rememberSaveable(draft.key) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if(!enabled)chooseGroup=false }
    Column(Modifier.fillMaxSize().testTag("phrase-details-form")
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PhraseEditorSection("字幕与标题") {
            RichPhraseTextField(draft, model, enabled)
            OutlinedTextField(title, { value -> title = value.copy(text=value.text.replace('\n',' ')); model.content.editPhrase { it.copy(title = title.text) } },
                interactionSource = titleSource, colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled, titleFocused),
                enabled = enabled, label = { Text("缩略标题（可选）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("phrase-detail-title").textMenuAnchor { title=title.copy(selection=TextRange(title.selection.end)) })
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
        PhraseEditorSection("附加内容") {
            PhraseTypeTabs(draft.type,enabled) { type->
                if(type!=draft.type) { focus.clearFocus();keyboard?.hide();model.content.editPhrase { it.switchType(type) } }
            }
            PhraseTypeContent(draft,model,enabled) { focus.clearFocus();keyboard?.hide() }
        }
        PhraseAppearanceSection(appearance,enabled,{ focus.clearFocus();keyboard?.hide();appearance=!appearance }) {
            PhraseAppearanceSettings(draft, model, enabled&&appearance)
        }
    }
}
