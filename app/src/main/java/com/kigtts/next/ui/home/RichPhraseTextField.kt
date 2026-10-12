package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.MotionIconButton

import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import com.kigtts.next.ui.components.*

@Composable
internal fun RichPhraseTextField(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    var field by rememberSaveable(draft.key, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.text)) }
    val rich = draft.richText
    var color by remember { mutableStateOf(false) }; var size by remember { mutableStateOf(false) }
    var oversized by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(draft.text) { if (field.text != draft.text) field = field.copy(text = draft.text) }
    LaunchedEffect(rich,enabled) { if(!rich||!enabled) { color=false;size=false } }
    fun format(kind: TextFormat, value: Long = 0) { val range = field.selection
        model.content.editPhrase { it.copy(formats = TextFormatting.apply(it.formats, it.text.length, range.start, range.end, kind, value)) }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text("富文本",Modifier.weight(1f))
        Switch(rich,feedbackChange { value->model.content.editPhrase { it.copy(richText=value) } },
            enabled=enabled,colors=SwitchDefaults.colors().animateEnabled(enabled),modifier=Modifier.testTag("phrase-rich-text"))
        MotionIconButton(feedbackClick { focus.clearFocus();keyboard?.hide();model.content.editor(target="phrase:"+draft.key) },
            enabled=enabled,modifier=Modifier.testTag("phrase-open-text-editor")) { Icon(Icons.Default.EditNote,"打开文本编辑器") }
    }
    OutlinedTextField(field, { next ->
        oversized = next.text.toByteArray().size > 128 * 1024
        if (!oversized) { field = next; model.content.editPhrase { it.copy(text = next.text, formats = TextFormatting.rebase(it.formats, it.text, next.text)) } }
    }, Modifier.fillMaxWidth().testTag("phrase-detail-text"), enabled = enabled, label = { Text("字幕（必填）") },
        minLines = 3, maxLines = 12, isError = oversized,
        interactionSource = source, colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled, focused, oversized),
        visualTransformation = if (rich) VisualTransformation { TransformedText(formattedText(it.text, draft.formats), OffsetMapping.Identity) } else VisualTransformation.None)
    if (oversized) Text("内容太长，请缩短后再保存", color = MaterialTheme.colorScheme.error)
    AnimatedVisibility(rich,enter=fadeIn(tween(160))+expandVertically(tween(220)),exit=fadeOut(tween(100))+shrinkVertically(tween(200))) {
        Column {
        val selected = rich && !field.selection.collapsed && enabled
        Row(Modifier.testTag("phrase-format-toolbar")) {
            MotionIconButton(feedbackClick { format(TextFormat.BOLD) }, enabled = selected, modifier = Modifier.testTag("phrase-format-bold")) { Icon(Icons.Default.FormatBold, "粗体") }
            MotionIconButton(feedbackClick { format(TextFormat.ITALIC) }, enabled = selected, modifier = Modifier.testTag("phrase-format-italic")) { Icon(Icons.Default.FormatItalic, "斜体") }
            MotionIconButton(feedbackClick { format(TextFormat.UNDERLINE) }, enabled = selected, modifier = Modifier.testTag("phrase-format-underline")) { Icon(Icons.Default.FormatUnderlined, "下划线") }
            MotionIconButton(feedbackClick { color = true }, enabled = selected) { Icon(Icons.Default.FormatColorText, "文字颜色") }
            Box {
                MotionIconButton(feedbackClick { size = true }, enabled = selected) { Icon(Icons.Default.FormatSize, "字号") }
                DropdownMenu(size, { size = false }) { listOf("小" to 75L, "正常" to 100L, "大" to 125L).forEach { (label, value) ->
                    DropdownMenuItem({ Text(label) }, feedbackClick { format(TextFormat.SIZE, value); size = false })
                } }
            }
        }
        if (!selected) Text("选中文字后设置格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (color) ColorPickerDialog(0xFF00696E, { format(TextFormat.COLOR, it); color = false }, { color = false }, title = "文字颜色")
}
