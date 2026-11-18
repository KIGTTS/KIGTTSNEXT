@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ChevronRight
import com.kigtts.next.ui.settings.*

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
    LaunchedEffect(enabled) { if(!enabled)chooseGroup=false }
    SettingsLayout {
        SettingsGroup("字幕与标题", modifier = Modifier.testTag("phrase-details-form")) {
            PhraseEditorRow(SettingPosition.FIRST) { RichPhraseTextField(draft, model, enabled) }
            PhraseEditorRow(SettingPosition.MIDDLE) {
            OutlinedTextField(title, { value -> title = value.copy(text=value.text.replace('\n',' ')); model.content.editPhrase { it.copy(title = title.text) } },
                visualTransformation = com.kigtts.next.ui.components.rememberInputTextMotion(title, MaterialTheme.colorScheme.onSurface),
                interactionSource = titleSource, colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled, titleFocused),
                enabled = enabled, label = { Text(uiText("quick.short_title")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("phrase-detail-title").textMenuAnchor { title=title.copy(selection=TextRange(title.selection.end)) })
            }
            Box {
                SettingItem("分组", draft.category, Icons.Default.Folder, SettingPosition.LAST,
                    Modifier.testTag("phrase-detail-category"), enabled = enabled,
                    onClick = { focus.clearFocus();keyboard?.hide();chooseGroup = true },
                    trailing = { Icon(Icons.Default.ChevronRight, null) })
                DropdownMenu(chooseGroup && enabled, { chooseGroup = false }) {
                    categories.forEach { group -> DropdownMenuItem(text = { Text(group) }, onClick = feedbackClick {
                        model.content.editPhrase { it.copy(category = group) }; chooseGroup = false
                    }) }
                }
            }
        }
        PhraseEditorSection("卡片外观", Modifier.testTag("phrase-appearance-section")) {
            PhraseAppearanceSettings(draft, model, enabled)
        }
        SettingsGroup("附加内容", modifier = Modifier.testTag("phrase-attachments-section")) {
          PhraseEditorRow(if (draft.type == QuickCardType.TEXT) SettingPosition.SINGLE else SettingPosition.FIRST) {
            PhraseTypeTabs(draft.type,enabled) { type->
                if(type!=draft.type) { focus.clearFocus();keyboard?.hide();model.content.editPhrase { it.switchType(type) } }
            }
          }
            PhraseTypeContent(draft,model,enabled) { focus.clearFocus();keyboard?.hide() }
        }
    }
}
