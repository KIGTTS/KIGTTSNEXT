package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.state.*

@Composable
fun PhraseDetailsDialog(draft: PhraseDraft, categories: List<String>, model: AppViewModel, dismiss: () -> Unit) {
    val text = rememberTextFieldState(initialText = draft.text)
    val description = rememberTextFieldState(initialText = draft.description)
    fun synchronize() { model.content.editPhrase { it.copy(text = text.text.toString(), description = description.text.toString()) } }
    var chooseGroup by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (draft.phraseId == null) "添加快捷文本" else "快捷文本详情") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state = text, label = { Text(if (draft.mediaId == null) "文本内容" else "条目名称") }, lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
                    modifier = Modifier.fillMaxWidth().testTag("phrase-detail-text"))
                OutlinedTextField(state = description, label = { Text("辅助说明（可选）") }, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    modifier = Modifier.fillMaxWidth().testTag("phrase-detail-description"))
                val media = model.mediaRepository.get(draft.mediaId)
                if (media != null) {
                    Text("${media.kind.label} · ${media.name}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(feedbackClick { model.content.editPhrase { it.copy(mediaId = null) } }, Modifier.testTag("detach-phrase-media")) { Text("移除媒体关联") }
                }
                Row {
                    TextButton(feedbackClick { synchronize(); model.content.picker("phrase:" + draft.key) }, Modifier.testTag("phrase-choose-media")) { Text("选择媒体") }
                    TextButton(feedbackClick { synchronize(); model.content.editor(html = media?.kind == com.kigtts.next.media.MediaKind.HTML, target = "phrase:" + draft.key) }) { Text("编辑内容") }
                }
                Box {
                    OutlinedButton(onClick = feedbackClick { chooseGroup = true }, modifier = Modifier.testTag("phrase-detail-category")) { Text("分组：" + draft.category) }
                    DropdownMenu(chooseGroup, { chooseGroup = false }) {
                        categories.forEach { group -> DropdownMenuItem(text = { Text(group) }, onClick = feedbackClick { model.content.editPhrase { it.copy(category = group) }; chooseGroup = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = feedbackClick {
            synchronize(); model.content.savePhrase(); model.category(draft.category); dismiss()
        }, enabled = text.text.isNotBlank(), modifier = Modifier.testTag("save-phrase-details")) { Text("保存") } },
        dismissButton = { TextButton(onClick = feedbackClick(dismiss)) { Text("取消") } })
}
