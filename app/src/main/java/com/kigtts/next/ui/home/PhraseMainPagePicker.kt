package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.HtmlProjectItem
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

/** Only traverses this attached HTML project's root; never opens the global media catalog. */
@Composable internal fun PhraseMainPagePicker(model: AppViewModel, mediaId: String, close: () -> Unit) {
    var directory by remember(mediaId) { mutableStateOf("") }
    var items by remember { mutableStateOf(emptyList<HtmlProjectItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(mediaId, directory) {
        try { items = withContext(Dispatchers.IO) { model.mediaRepository.htmlFiles.list(mediaId, directory).filter { it.directory || it.name.substringAfterLast('.').lowercase() in setOf("html", "htm") } }; error = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.message }
    }
    MotionAlertDialog(onDismissRequest = close, title = { Text("选择主页面") }, text = {
        Column(Modifier.heightIn(max = 420.dp)) {
            Text(if (directory.isBlank()) "当前词条的文件" else directory, style = MaterialTheme.typography.labelMedium)
            if (directory.isNotBlank()) MotionTextButton(onClick = feedbackClick { directory = directory.substringBeforeLast('/', "") }, enabled = !busy) { Text("上一级") }
            LazyColumn { items(items, key = { it.path }) { item ->
                TextButton(enabled = !busy, onClick = feedbackClick {
                    if (item.directory) directory = item.path else scope.launch {
                        busy = true
                        try { withContext(Dispatchers.IO) { model.mediaRepository.htmlFiles.selectMain(mediaId, item.path) }; close() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (failure: Exception) { error = failure.message }
                        finally { busy = false }
                    }
                }) { Icon(if (item.directory) Icons.Default.Folder else if (item.main) Icons.Default.Check else Icons.Default.Html, null); Spacer(Modifier.width(12.dp)); Text(item.name) }
            } }
            if (items.isEmpty()) Text("这个文件夹中没有网页文件")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(close)) { Text("取消") } })
}
