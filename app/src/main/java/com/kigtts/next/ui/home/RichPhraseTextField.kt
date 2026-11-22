package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.*
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.RegisterPageInput

@Composable
internal fun RichPhraseTextField(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    var field by rememberSaveable(draft.key, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.text)) }
    var oversized by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    RegisterPageInput(focused)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val base = remember(draft.formats) { RichTextVisualTransformation(draft.formats) }
    val transform = rememberInputTextMotion(field, MaterialTheme.colorScheme.onSurface, base)
    val menu = Modifier.richTextMenu(enabled, { field.selection }, draft.formats) { range, kind, value ->
        model.content.editPhrase { it.copy(richText = true,
            formats = TextFormatting.apply(it.formats, it.text.length, range.start, range.end, kind, value)) }
    }
    LaunchedEffect(draft.text) { if (field.text != draft.text) field = field.copy(text = draft.text) }
    OutlinedTextField(field, { next ->
        oversized = next.text.toByteArray().size > 128 * 1024
        if (!oversized) { field = next; model.content.editPhrase { it.copy(text = next.text,
            formats = TextFormatting.rebase(it.formats, it.text, next.text)) } }
    }, Modifier.fillMaxWidth().testTag("phrase-detail-text").then(menu).textMenuAnchor {
        field=field.copy(selection=androidx.compose.ui.text.TextRange(field.selection.end))
    }, enabled = enabled, label = { Text(if (draft.type == com.kigtts.next.state.QuickCardType.TEXT) uiText("quick.caption_required") else uiText("quick.caption_optional")) },
        minLines = 3, maxLines = 12, isError = oversized, interactionSource = source,
        colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled, focused, oversized), visualTransformation = transform,
        trailingIcon = {
            MotionIconButton(feedbackClick { focus.clearFocus(); keyboard?.hide(); model.content.editor(target = "phrase:" + draft.key) },
                enabled = enabled, modifier = Modifier.testTag("phrase-open-text-editor")) { Icon(Icons.Default.EditNote, uiText("accessibility.open_text_editor")) }
        })
    if (oversized) Text(uiText("quick.too_long"), color = MaterialTheme.colorScheme.error)
}
