package com.kigtts.next.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun FontSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit, fromStore: (() -> Unit)? = null) {
    val context = LocalContext.current
    val installed = remember { InstalledFonts(context) }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var locals by remember { mutableStateOf<List<InstalledFont>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
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
    ReadablePage(840.dp) {
        LazyColumn(Modifier.fillMaxSize().testTag("font-settings-list"), contentPadding = com.kigtts.next.ui.navigation.pageContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { FilledTonalButton(feedbackClick { if (busy == null) picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("导入字体")
            } }
            fromStore?.let { open -> item {
                OutlinedButton(feedbackClick(open), Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Storefront, null); Spacer(Modifier.width(8.dp)); Text("从商店获取")
                }
            } }
            if (busy != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Text("我的字体", style = MaterialTheme.typography.titleMedium) }
            item { FontChoiceRow("系统默认", settings.font == null, { onChange(current.copy(font = null, fontName = "系统默认")) }) }
            items(locals, key = { it.key }) { font ->
                val selected = settings.font == font.key
                val face = rememberAppearanceFont(font.key, if (selected) settings.fontWeight else 400)
                val axis by produceState<FontWeightAxis?>(null, font.key) {
                    value = withContext(Dispatchers.IO) { AppearanceAssets(context).file(font.key)?.let(::readFontWeightAxis) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoiceRow(font.name, selected, { onChange(current.copy(font = font.key, fontName = font.name)) }) {
                        Text("你好，KIGTTS · Aa 123", style = face.apply(TextStyle(fontSize = 24.sp, lineHeight = 32.sp)), maxLines = 1)
                    }
                    if (selected) axis?.let { range -> FontWeightControl(range, settings.fontWeight) {
                        onChange(current.copy(fontWeight = it))
                    } }
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
