package com.kigtts.next.ui.media

import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.MotionTextButton

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import kotlinx.coroutines.launch
import com.kigtts.next.text.*
import com.kigtts.next.ui.components.richTextMenu
import com.kigtts.next.ui.components.textMenuAnchor
import com.kigtts.next.ui.navigation.pageInputFocus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun ContentEditorScreen(model: AppViewModel, token: String) {
    val draft by model.content.editorDraft.collectAsStateWithLifecycle()
    val pending by model.content.discardRequested.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    val entry = draft?.takeIf { it.token == token } ?: return
    if(entry.html) { com.kigtts.next.ui.media.code.HtmlCodeEditorScreen(model,entry,pending);return }
    val editor = remember(token) { RichEditorState(entry.value, entry.formats, entry.selection) }
    val value = editor.value.text
    var title by rememberSaveable(token) { mutableStateOf(entry.currentTitle) }
    var search by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun change(text: String) {
        if (text == value) return
        try { editor.change(TextFieldValue(text,TextRange(text.length))); error = null }
        catch (e:IllegalArgumentException) { error=e.message }
    }
    LaunchedEffect(editor.value, editor.formats, title) { model.content.editEditor(value, title, editor.formats, editor.value.selection.end) }
    val transform = remember(editor.formats) { RichTextVisualTransformation(editor.formats) }
    val menu = Modifier.richTextMenu(active&&!saving, { editor.value.selection }, editor.formats, editor::format)
    RegisterPageActions(listOf(
        ToolbarAction("save-content","保存内容",Icons.Default.Save,!saving) {
            scope.launch { saving=true;try {
                model.content.editEditor(editor.value.text,title,editor.formats,editor.value.selection.end)
                model.content.saveEditor(editor.value.text,title)
            } catch(e:Exception) { error=e.message ?: "保存失败" } finally { saving=false } }
        },
        ToolbarAction("find-content","查找替换",Icons.Default.FindReplace) { search=true }
    ))
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            MotionIconButton(feedbackClick(editor::undo), enabled = active&&!saving&&editor.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "撤销") }
            MotionIconButton(feedbackClick(editor::redo), enabled = active&&!saving&&editor.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "重做") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(editor.value, { next -> try { editor.change(next);error=null } catch(e:IllegalArgumentException) { error=e.message } },
            Modifier.fillMaxWidth().weight(1f).testTag("content-editor-text").then(menu).pageInputFocus()
                .textMenuAnchor { editor.change(editor.value.copy(selection=TextRange(editor.value.selection.end))) },
            enabled=active&&!saving, label = { Text("文本内容") }, textStyle = MaterialTheme.typography.bodyLarge, visualTransformation=transform)
    }
    if (active && pending) MotionAlertDialog(onDismissRequest = model.content::keepEditor, title = { Text("放弃未保存的修改？") },
        confirmButton = { MotionTextButton(dialogFeedbackClick(model.content::discardEditor)) { Text("放弃") } },
        dismissButton = { MotionTextButton(dialogFeedbackClick(model.content::keepEditor)) { Text("继续编辑") } })
    if (active && search) {
        var find by remember { mutableStateOf("") }; var replacement by remember { mutableStateOf("") }
        MotionAlertDialog(onDismissRequest = { search = false }, title = { Text("查找替换") }, text = { Column {
            OutlinedTextField(find, { find = it }, label = { Text("查找") }); OutlinedTextField(replacement, { replacement = it }, label = { Text("替换为") })
            if (find.isNotEmpty()) Text("找到 " + Regex(Regex.escape(find)).findAll(value).count() + " 处")
        } }, confirmButton = { MotionTextButton(dialogFeedbackClick { change(value.replace(find, replacement)); search = false }, enabled = find.isNotEmpty()) { Text("全部替换") } },
            dismissButton = { MotionTextButton(dialogFeedbackClick { search = false }) { Text("取消") } })
    }
}
