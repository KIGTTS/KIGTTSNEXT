package com.kigtts.next.ui.media.code

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.launch

@Composable
fun HtmlCodeEditorScreen(model:AppViewModel,entry:ContentDraft,pending:Boolean) {
    val editor=remember(entry.token) { CodeEditorState(entry.value,entry.selection) };val focus=remember { FocusRequester() }
    val scope=rememberCoroutineScope();val active=LocalPageActive.current
    var saving by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) };var search by remember { mutableStateOf(false) }
    LaunchedEffect(editor.value) { model.content.editEditor(editor.value.text,entry.currentTitle);model.content.editorSelection(editor.value.selection.end) }
    RegisterPageLoading(saving)
    RegisterPageActions(listOf(
        ToolbarAction("save-content","保存文件",Icons.Default.Save,!saving) { scope.launch { saving=true
            try { model.content.saveEditor(editor.value.text,entry.currentTitle) } catch(e:Exception) { error=e.message ?: "保存失败" } finally { saving=false }
        } },
        ToolbarAction("find-content","查找替换",Icons.Default.FindReplace,!saving) { search=true }
    ))
    Column(Modifier.fillMaxSize().imePadding()) {
        AnimatedVisibility(error!=null) { Text(error.orEmpty(),Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error) }
        CodeTextArea(editor,active&&!saving,focus,Modifier.fillMaxWidth().weight(1f)) { error=it }
        CodeShortcutBar(editor,active&&!saving,{ focus.requestFocus() }) { key ->
            try { editor.invoke(key);focus.requestFocus() } catch(e:Exception) { error=e.message }
        }
    }
    if(active&&pending)MotionAlertDialog(onDismissRequest=model.content::keepEditor,title={Text("放弃未保存的修改？")},
        confirmButton={MotionTextButton(dialogFeedbackClick(model.content::discardEditor)) {Text("放弃")} },
        dismissButton={MotionTextButton(dialogFeedbackClick(model.content::keepEditor)) {Text("继续编辑")} })
    if(active&&search) {
        var find by remember { mutableStateOf("") };var replacement by remember { mutableStateOf("") }
        MotionAlertDialog(onDismissRequest={search=false},title={Text("查找替换")},text={Column {
            OutlinedTextField(find,{find=it},label={Text("查找")});OutlinedTextField(replacement,{replacement=it},label={Text("替换为")})
        }},confirmButton={MotionTextButton(dialogFeedbackClick {
            try { editor.change(TextFieldValue(editor.value.text.replace(find,replacement),TextRange.Zero));search=false } catch(e:Exception) { error=e.message }
        },enabled=find.isNotEmpty()) {Text("全部替换")} },dismissButton={MotionTextButton(dialogFeedbackClick {search=false}) {Text("取消")} })
    }
}
