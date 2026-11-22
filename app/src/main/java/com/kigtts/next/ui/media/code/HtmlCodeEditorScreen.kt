package com.kigtts.next.ui.media.code

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart

@Composable
fun HtmlCodeEditorScreen(model:AppViewModel,entry:ContentDraft,pending:Boolean) {
    val editor=rememberSaveable(entry.token,saver=codeEditorStateSaver()) { CodeEditorState(entry.value,entry.selection) };val focus=remember { FocusRequester() }
    val scope=rememberCoroutineScope();val active=LocalPageActive.current
    val savePending by model.content.editorSavePending.collectAsStateWithLifecycle()
    val saveCompleted by model.content.editorSaveCompleted.collectAsStateWithLifecycle()
    var localSaving by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) };var search by remember { mutableStateOf(false) }
    val saving=localSaving||savePending
    LaunchedEffect(editor.value) { model.content.editEditor(editor.value.text,entry.currentTitle,selection=editor.value.selection.end) }
    fun saveDraft() {
        if(!active||saving)return
        localSaving=true;error=null
        scope.launch(start=CoroutineStart.UNDISPATCHED) {
            try {
                model.content.editEditor(editor.value.text,entry.currentTitle,selection=editor.value.selection.end)
                model.content.saveEditor(editor.value.text,entry.currentTitle)
            } catch(cancel:CancellationException) {throw cancel}
            catch(failure:Exception) {error=failure.message ?: "保存失败，请重试"}
            finally {localSaving=false}
        }
    }
    RegisterPageLoading(saving)
    RegisterPageBackGuard(active&&saving) { }
    RegisterPageActions(listOf(
        ToolbarAction("save-content",uiText("file.save"),Icons.Default.Save,active&&!saving) {saveDraft()},
        ToolbarAction("find-content",uiText("editor.find_replace"),Icons.Default.FindReplace,!saving) { search=true }
    ))
    Column(Modifier.fillMaxSize().imePadding()) {
        AnimatedVisibility(error!=null) { Text(error.orEmpty(),Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error) }
        CodeTextArea(editor,active&&!saving,focus,Modifier.fillMaxWidth().weight(1f)) { error=it }
        CodeShortcutBar(editor,active&&!saving,{ focus.requestFocus() }) { key ->
            try { editor.invoke(key);focus.requestFocus() } catch(e:Exception) { error=e.message }
        }
    }
    if(active&&pending)SaveChangesDialog(save={_->saveDraft()},
        discard=model.content::discardEditor,keepEditing=model.content::keepEditor,busy=saving,error=error,
        completed=if(saveCompleted)({model.content.cleanupEditorSave(entry.token)})else null)
    if(active&&search) {
        var find by remember { mutableStateOf("") };var replacement by remember { mutableStateOf("") }
        MotionAlertDialog(onDismissRequest={search=false},title={Text(uiText("editor.find_replace"))},text={Column {
            OutlinedTextField(find,{find=it},label={Text(uiText("editor.find"))});OutlinedTextField(replacement,{replacement=it},label={Text(uiText("editor.replacement"))})
        }},confirmButton={MotionTextButton(dialogFeedbackClick {
            try { editor.change(TextFieldValue(editor.value.text.replace(find,replacement),TextRange.Zero));search=false } catch(e:Exception) { error=e.message }
        },enabled=find.isNotEmpty()) {Text(uiText("editor.replace_all"))} },dismissButton={MotionTextButton(dialogFeedbackClick {search=false}) {Text(uiText("action.cancel"))} })
    }
}
