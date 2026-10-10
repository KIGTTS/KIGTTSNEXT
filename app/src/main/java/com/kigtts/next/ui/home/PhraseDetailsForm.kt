@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun PhraseDetailsForm(draft: PhraseDraft, categories: List<String>, model: AppViewModel,
    enabled: Boolean, close: (() -> Unit) -> Unit, dismiss: () -> Unit) {
    val text = rememberTextFieldState(initialText = draft.text)
    val description = rememberTextFieldState(initialText = draft.description)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun synchronize() { model.content.editPhrase { it.copy(text = text.text.toString(), description = description.text.toString()) } }
    fun finish(action: () -> Unit) { focus.clearFocus(); keyboard?.hide(); close(action) }
    LaunchedEffect(text, description) {
        snapshotFlow { text.text.toString() to description.text.toString() }.collect { (value, detail) ->
            model.content.editPhrase { it.copy(text = value, description = detail) }
        }
    }
    var chooseGroup by remember { mutableStateOf(false) }
    Scaffold(Modifier.fillMaxSize().testTag("phrase-fullscreen-form"), containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = { TopAppBar(title = { Text(if (draft.phraseId == null) "添加快捷文本" else "编辑快捷文本") },
            navigationIcon = { IconButton(feedbackClick { finish(dismiss) }, enabled = enabled,
                modifier = Modifier.testTag("cancel-phrase-details")) { Icon(Icons.Default.Close, "取消") } },
            actions = { TextButton(feedbackClick {
                synchronize(); finish { model.content.savePhrase(); model.category(draft.category); dismiss() }
            }, enabled = enabled && text.text.isNotBlank(), modifier = Modifier.testTag("save-phrase-details")) { Text("保存") } }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(state = text, enabled = enabled, label = { Text(if (draft.mediaId == null) "文本内容" else "条目名称") },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 12),
                modifier = Modifier.fillMaxWidth().testTag("phrase-detail-text"))
            OutlinedTextField(state = description, enabled = enabled, label = { Text("辅助说明（可选）") },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4), modifier = Modifier.fillMaxWidth().testTag("phrase-detail-description"))
            val media = model.mediaRepository.get(draft.mediaId)
            if (media != null) {
                Text("${media.kind.label} · ${media.name}", style = MaterialTheme.typography.bodyMedium)
                TextButton(feedbackClick { model.content.editPhrase { it.copy(mediaId = null) } }, enabled = enabled,
                    modifier = Modifier.testTag("detach-phrase-media")) { Text("移除媒体关联") }
            }
            Row {
                TextButton(feedbackClick { synchronize(); focus.clearFocus(); keyboard?.hide(); model.content.picker("phrase:" + draft.key) },
                    enabled = enabled, modifier = Modifier.testTag("phrase-choose-media")) { Text("选择媒体") }
                TextButton(feedbackClick { synchronize(); focus.clearFocus(); keyboard?.hide()
                    model.content.editor(html = media?.kind == com.kigtts.next.media.MediaKind.HTML, target = "phrase:" + draft.key)
                }, enabled = enabled) { Text("编辑内容") }
            }
            Box {
                OutlinedButton(feedbackClick { chooseGroup = true }, enabled = enabled, modifier = Modifier.testTag("phrase-detail-category")) {
                    Text("分组：" + draft.category)
                }
                DropdownMenu(chooseGroup, { chooseGroup = false }) {
                    categories.forEach { group -> DropdownMenuItem(text = { Text(group) }, onClick = feedbackClick {
                        model.content.editPhrase { it.copy(category = group) }; chooseGroup = false
                    }) }
                }
            }
        }
    }
}
