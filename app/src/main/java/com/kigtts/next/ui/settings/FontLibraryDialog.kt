package com.kigtts.next.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun FontLibraryDialog(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember { FontCatalog(context) }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var fonts by remember { mutableStateOf<List<CatalogFont>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true; error = null
        try { fonts = withContext(Dispatchers.IO) { catalog.list() } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "暂时无法连接字体库，你也可以导入本地字体。" }
        finally { loading = false }
    }
    MotionAlertDialog(onDismissRequest = onDismiss, title = { Text("选择字体") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ListItem(headlineContent = { Text("系统默认") }, trailingContent = { RadioButton(settings.font == null,
                feedbackClick { onChange(current.copy(font = null, fontName = "系统默认")); onDismiss() }) },
                modifier = Modifier.clickable { onChange(current.copy(font = null, fontName = "系统默认")); onDismiss() }) }
            if (loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(fonts, key = { it.id }) { item ->
                val preview by produceState<android.graphics.Bitmap?>(null, item.id) {
                    value = withContext(Dispatchers.IO) { runCatching { catalog.preview(item) }.getOrNull() }
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium); Text(item.license, style = MaterialTheme.typography.labelSmall) }
                            if (selected == item.id) CircularProgressIndicator(Modifier.size(24.dp)) else IconButton(feedbackClick {
                                if (selected == null) scope.launch {
                                    selected = item.id
                                    try { val key = withContext(Dispatchers.IO) { catalog.install(item) }; onChange(current.copy(font = key, fontName = item.name)); onDismiss() }
                                    catch (cancel: CancellationException) { throw cancel }
                                    catch (e: Exception) { error = e.message ?: "下载失败，请重试" }
                                    finally { selected = null }
                                }
                            }) { Icon(Icons.Default.Download, "下载并使用${item.name}") }
                        }
                        preview?.let { Image(it.asImageBitmap(), "${item.name}预览", Modifier.fillMaxWidth().height(64.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick(onDismiss)) { Text("完成") } },
        dismissButton = { TextButton(feedbackClick { refresh++ }) { Text("刷新") } })
}
