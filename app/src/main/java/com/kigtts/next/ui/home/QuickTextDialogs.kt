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
fun PhraseDetailsDialog(phrase: QuickPhrase?, selectedCategory: String, categories: List<String>, model: AppViewModel, dismiss: () -> Unit) {
    val text = rememberTextFieldState(initialText = phrase?.text ?: "")
    val description = rememberTextFieldState(initialText = phrase?.description ?: "")
    var category by remember(phrase?.id) { mutableStateOf(phrase?.category ?: selectedCategory) }
    var chooseGroup by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (phrase == null) "添加快捷文本" else "快捷文本详情") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state = text, label = { Text("文本内容") }, lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
                    modifier = Modifier.fillMaxWidth().testTag("phrase-detail-text"))
                OutlinedTextField(state = description, label = { Text("辅助说明（可选）") }, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    modifier = Modifier.fillMaxWidth().testTag("phrase-detail-description"))
                Box {
                    OutlinedButton(onClick = feedbackClick { chooseGroup = true }, modifier = Modifier.testTag("phrase-detail-category")) { Text("分组：" + category) }
                    DropdownMenu(chooseGroup, { chooseGroup = false }) {
                        categories.forEach { group -> DropdownMenuItem(text = { Text(group) }, onClick = feedbackClick { category = group; chooseGroup = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = feedbackClick {
            if (phrase == null) model.quickTexts.add(text.text.toString(), category, description.text.toString())
            else model.quickTexts.edit(phrase.id, text.text.toString(), description.text.toString(), category)
            model.category(category); dismiss()
        }, enabled = text.text.isNotBlank(), modifier = Modifier.testTag("save-phrase-details")) { Text("保存") } },
        dismissButton = { TextButton(onClick = feedbackClick(dismiss)) { Text("取消") } })
}
