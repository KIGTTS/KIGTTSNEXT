package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.MotionIconButton

import androidx.compose.foundation.layout.*
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
    val rich = draft.type == QuickCardType.TEXT && draft.richText
    var color by remember { mutableStateOf(false) }; var size by remember { mutableStateOf(false) }
    var oversized by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(draft.text) { if (field.text != draft.text) field = field.copy(text = draft.text) }
    fun format(kind: TextFormat, value: Long = 0) { val range = field.selection
        model.content.editPhrase { it.copy(formats = TextFormatting.apply(it.formats, it.text.length, range.start, range.end, kind, value)) }
    }
    OutlinedTextField(field, { next ->
        oversized = next.text.toByteArray().size > 128 * 1024
        if (!oversized) { field = next; model.content.editPhrase { it.copy(text = next.text, formats = TextFormatting.rebase(it.formats, it.text, next.text)) } }
    }, Modifier.fillMaxWidth().testTag("phrase-detail-text"), enabled = enabled, label = { Text("文本内容") },
        minLines = 3, maxLines = 12, isError = oversized,
        interactionSource = source, colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled, focused, oversized),
        visualTransformation = if (rich) VisualTransformation { TransformedText(formattedText(it.text, draft.formats), OffsetMapping.Identity) } else VisualTransformation.None)
    if (oversized) Text("内容太长，请缩短后再保存", color = MaterialTheme.colorScheme.error)
    if (rich) {
        val selected = !field.selection.collapsed && enabled
        Row(Modifier.testTag("phrase-format-toolbar")) {
            MotionIconButton(feedbackClick { format(TextFormat.BOLD) }, enabled = selected, modifier = Modifier.testTag("phrase-format-bold")) { Icon(Icons.Default.FormatBold, "粗体") }
            MotionIconButton(feedbackClick { format(TextFormat.ITALIC) }, enabled = selected, modifier = Modifier.testTag("phrase-format-italic")) { Icon(Icons.Default.FormatItalic, "斜体") }
            MotionIconButton(feedbackClick { color = true }, enabled = selected) { Icon(Icons.Default.FormatColorText, "文字颜色") }
            Box {
                MotionIconButton(feedbackClick { size = true }, enabled = selected) { Icon(Icons.Default.FormatSize, "字号") }
                DropdownMenu(size, { size = false }) { listOf("小" to 75L, "默认" to 100L, "大" to 125L, "特大" to 150L,
                    "两倍" to 200L,"三倍" to 300L,"四倍" to 400L).forEach { (label, value) ->
                    DropdownMenuItem({ Text(label) }, feedbackClick { format(TextFormat.SIZE, value); size = false })
                } }
            }
        }
        if (!selected) Text("选中文字后设置格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (color) ColorPickerDialog(0xFF00696E, { format(TextFormat.COLOR, it); color = false }, { color = false }, title = "文字颜色")
}
