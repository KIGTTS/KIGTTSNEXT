package com.kigtts.next.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun PhraseIconSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var symbols by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    fun change(values: Map<String, String>) { model.content.editPhrase { current -> if (current.key != draft.key) current else current.copy(metadata = current.metadata.filterKeys { !it.startsWith("icon.") } + values) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        importing = true; error = null
        try { val id = withContext(Dispatchers.IO) { model.mediaRepository.icons.import(uri) }; change(mapOf("icon.asset" to id)) }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.message }
        finally { importing = false }
    } }
    Text("条目图标", style = MaterialTheme.typography.titleSmall)
    Row {
        MotionTextButton(onClick = feedbackClick { symbols = true }, enabled = enabled && !importing) { Text("选择图标") }
        MotionTextButton(onClick = feedbackClick { picker.launch(arrayOf("image/*", "image/svg+xml")) }, enabled = enabled && !importing) { Text("导入图片或SVG") }
        if (draft.metadata.keys.any { it.startsWith("icon.") }) MotionTextButton(onClick = feedbackClick { change(emptyMap()) }, enabled = enabled) { Text("恢复默认") }
    }
    if (draft.metadata["icon.asset"] != null) Row {
        Text("用作背景缩略图", Modifier.weight(1f))
        Switch(draft.metadata["icon.mode"] == "thumbnail", { value -> model.content.editPhrase { it.copy(metadata = it.metadata + ("icon.mode" to if(value) "thumbnail" else "icon")) } }, enabled = enabled)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (symbols) MaterialSymbolPicker({ name -> change(mapOf("icon.symbol" to name)); symbols = false }, { symbols = false })
}

@Composable private fun MaterialSymbolPicker(select: (String) -> Unit, close: () -> Unit) {
    var query by remember { mutableStateOf("") }; var offset by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf(emptyList<Pair<String, ImageVector?>>()) }; var more by remember { mutableStateOf(false) }
    LaunchedEffect(query, offset) {
        delay(120)
        val found = withContext(Dispatchers.Default) { MaterialSymbols.search(query, offset, 81).map { it to MaterialSymbols.vector(it) } }
        more = found.size > 80; items = found.take(80)
    }
    MotionAlertDialog(onDismissRequest = close, title = { Text("Material Symbols") }, text = {
        Column {
            OutlinedTextField(query, { query = it.take(80); offset = 0 }, singleLine = true, label = { Text("搜索图标英文名") })
            LazyVerticalGrid(GridCells.Adaptive(72.dp), Modifier.height(320.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(items, key = { it.first }) { (name, vector) -> TextButton(onClick = feedbackClick { select(name) }) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { vector?.let { Icon(it, null) }; Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                } }
            }
            Row { if (offset > 0) MotionTextButton(onClick = feedbackClick { offset = (offset - 80).coerceAtLeast(0) }) { Text("上一页") }
                if (more) MotionTextButton(onClick = feedbackClick { offset += 80 }) { Text("下一页") } }
        }
    }, confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(close)) { Text("取消") } })
}
