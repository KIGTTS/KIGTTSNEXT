package com.kigtts.next.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun FontSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    val context = LocalContext.current
    val catalog = remember { FontCatalog(context) }
    val installed = remember { InstalledFonts(context) }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var locals by remember { mutableStateOf<List<InstalledFont>>(emptyList()) }
    var online by remember { mutableStateOf<List<CatalogFont>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun importFont(uri: android.net.Uri, name: String? = null) {
        busy = "import"
        try {
            val font = withContext(Dispatchers.IO) {
                val label = name ?: context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0).substringBeforeLast('.') else null
                } ?: "自定义字体"
                InstalledFont(AppearanceAssets(context).importFont(uri), label).also { installed.remember(it.key, it.name) }
            }
            onChange(current.copy(font = font.key, fontName = font.name)); revision++
        } catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) { error = e.message ?: "字体未能导入，请重试" }
        finally { busy = null }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { importFont(uri) }
    }
    LaunchedEffect(settings.font, revision) { locals = withContext(Dispatchers.IO) { installed.list(current) } }
    LaunchedEffect(refresh) {
        loading = true; error = null
        try { online = withContext(Dispatchers.IO) { catalog.list() } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "暂时无法连接字体库，你也可以导入本地字体。" }
        finally { loading = false }
    }
    ReadablePage(840.dp) {
        LazyColumn(Modifier.fillMaxSize().testTag("font-settings-list"), contentPadding = com.kigtts.next.ui.navigation.pageContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { FilledTonalButton(feedbackClick { if (busy == null) picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("导入字体")
            } }
            item { Text("我的字体", style = MaterialTheme.typography.titleMedium) }
            item { FontChoiceRow("系统默认", settings.font == null, { onChange(current.copy(font = null, fontName = "系统默认")) }) }
            items(locals, key = { it.key }) { font ->
                val face = rememberAppearanceFont(font.key)
                FontChoiceRow(font.name, settings.font == font.key, { onChange(current.copy(font = font.key, fontName = font.name)) }) {
                    Text("你好，KIGTTS · Aa 123", style = face.apply(MaterialTheme.typography.titleLarge), maxLines = 1)
                }
            }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("字体库", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(feedbackClick { refresh++ }) { Icon(Icons.Default.Refresh, "刷新字体库") }
            } }
            if (loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(online, key = { "online/" + it.id }) { font ->
                val preview by produceState<android.graphics.Bitmap?>(null, font.id) {
                    value = withContext(Dispatchers.IO) { runCatching { catalog.preview(font) }.getOrNull() }
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(font.name, style = MaterialTheme.typography.titleMedium)
                                Text(font.license, style = MaterialTheme.typography.bodySmall) }
                            if (busy == font.id) CircularProgressIndicator(Modifier.size(24.dp))
                            else IconButton(feedbackClick {
                                if (busy == null) scope.launch {
                                    busy = font.id
                                    try {
                                        val key = withContext(Dispatchers.IO) { catalog.install(font).also { installed.remember(it, font.name) } }
                                        onChange(current.copy(font = key, fontName = font.name)); revision++
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (e: Exception) { error = e.message ?: "下载失败，请重试" }
                                    finally { busy = null }
                                }
                            }) { Icon(Icons.Default.Download, "下载并使用${font.name}") }
                        }
                        preview?.let { Image(it.asImageBitmap(), "${font.name}预览", Modifier.fillMaxWidth().height(64.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)) }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable private fun FontChoiceRow(name: String, selected: Boolean, select: () -> Unit, preview: @Composable () -> Unit = {}) {
    Surface(onClick = feedbackClick(select), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(12.dp))
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                RadioButton(selected, null)
            }
            preview()
        }
    }
}
