package com.kigtts.next.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import kotlinx.coroutines.launch

@Composable
fun ContentEditorScreen(model: AppViewModel, token: String) {
    val draft by model.content.editorDraft.collectAsStateWithLifecycle()
    val pending by model.content.discardRequested.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    val entry = draft?.takeIf { it.token == token } ?: return
    var value by rememberSaveable(token) { mutableStateOf(entry.value) }
    var title by rememberSaveable(token) { mutableStateOf(entry.currentTitle) }
    val undo = remember(token) { ArrayDeque<String>() }; val redo = remember(token) { ArrayDeque<String>() }
    var revision by remember { mutableIntStateOf(0) }; var search by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun change(text: String) {
        if (text == value) return
        val maximum = if (entry.html) 2 * 1024 * 1024 else 128 * 1024
        if (text.toByteArray().size > maximum) { error = if (entry.html) "HTML 不能超过 2 MiB" else "文本不能超过 128 KiB"; return }
        undo.addLast(value); while (undo.size > 20 || undo.sumOf { it.length.toLong() } > 4L * 1024 * 1024) undo.removeFirst()
        redo.clear(); value = text; revision++
        model.content.editEditor(value, title)
    }
    LaunchedEffect(value, title) { model.content.editEditor(value, title) }
    RegisterPageActions(listOf(
        ToolbarAction("save-content","保存内容",Icons.Default.Save,!saving) {
            scope.launch { saving=true;try { model.content.saveEditor(value,title) } catch(e:Exception) { error=e.message ?: "保存失败" } finally { saving=false } }
        },
        ToolbarAction("find-content","查找替换",Icons.Default.FindReplace) { search=true }
    ))
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            key(revision) {
                IconButton(feedbackClick { if (undo.isNotEmpty()) { redo.addLast(value); value = undo.removeLast(); revision++ } }, enabled = undo.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "撤销") }
                IconButton(feedbackClick { if (redo.isNotEmpty()) { undo.addLast(value); value = redo.removeLast(); revision++ } }, enabled = redo.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Redo, "重做") }
            }
        }
        if (entry.html) OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("内容名称") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(value, ::change, Modifier.fillMaxWidth().weight(1f).testTag("content-editor-text"),
            label = { Text(if (entry.html) "HTML 源码" else "文本内容") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (entry.html) FontFamily.Monospace else FontFamily.Default))
    }
    if (active && pending) AlertDialog(onDismissRequest = model.content::keepEditor, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(feedbackClick(model.content::discardEditor)) { Text("放弃") } },
        dismissButton = { TextButton(feedbackClick(model.content::keepEditor)) { Text("继续编辑") } })
    if (active && search) {
        var find by remember { mutableStateOf("") }; var replacement by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { search = false }, title = { Text("查找替换") }, text = { Column {
            OutlinedTextField(find, { find = it }, label = { Text("查找") }); OutlinedTextField(replacement, { replacement = it }, label = { Text("替换为") })
            if (find.isNotEmpty()) Text("找到 " + Regex(Regex.escape(find)).findAll(value).count() + " 处")
        } }, confirmButton = { TextButton(feedbackClick { change(value.replace(find, replacement)); search = false }, enabled = find.isNotEmpty()) { Text("全部替换") } },
            dismissButton = { TextButton(feedbackClick { search = false }) { Text("取消") } })
    }
}
