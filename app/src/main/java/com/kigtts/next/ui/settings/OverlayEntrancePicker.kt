package com.kigtts.next.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.overlay.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.components.MotionAlertDialog as AlertDialog
import com.kigtts.next.i18n.rememberUiCopy
import kotlinx.coroutines.delay

@Composable internal fun OverlayEntrancePicker(existing: Set<String>, onDismiss: () -> Unit, add: (OverlayShortcut) -> Unit) {
    val context = LocalContext.current
    val copy = rememberUiCopy()
    val plugins by remember { OverlayHub.get(context).candidates }.collectAsState()
    var apps by remember { mutableStateOf<List<OverlayShortcut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(retry) {
        loading = true; failed = false
        runCatching { OverlayAppCatalog.list(context.applicationContext) }.onSuccess { apps = it }.onFailure { failed = true }
        loading = false
    }
    LaunchedEffect(Unit) {
        val runtime = HostPluginRuntime.get(context.applicationContext as Application)
        while (true) { refreshOverlayPlugins(context, runtime.manager); delay(1500) }
    }
    val builtIns = remember { overlayBuiltInChoices() }
    val categories = listOf("KIGTTS", "插件", "应用")
    val source = when (path.firstOrNull()) { "KIGTTS" -> builtIns; "插件" -> plugins; "应用" -> apps; else -> emptyList() }
    val searching = query.isNotBlank()
    val scoped = if (path.size > 1) source.filter { it.category == path[1] } else source
    val items = if (searching) (builtIns + plugins + apps).filter { it.title.contains(query, true) || it.category.contains(query, true) }
        else if (path.size > 2) scoped.filter { if (path[2] == "页面") it.target.startsWith("page:") else !it.target.startsWith("page:") } else scoped
    val sections = when {
        searching -> emptyList()
        path.isEmpty() -> categories
        path.size == 1 && path[0] != "应用" -> source.map { it.category }.distinct()
        path.size == 2 && path[0] == "插件" -> listOf("页面", "操作").filter { section -> scoped.any { if (section == "页面") it.target.startsWith("page:") else !it.target.startsWith("page:") } }
        else -> emptyList()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(copy("overlay.picker.title")) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text(copy("overlay.picker.search")) },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (path.isNotEmpty() && !searching) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = feedbackClick { path = path.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, copy("overlay.picker.back")) }
                Text(path.last(), style = MaterialTheme.typography.titleSmall)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (sections.isNotEmpty()) items(sections, key = { "section:$it" }) { title ->
                    TextButton(onClick = feedbackClick { path = path + title }, modifier = Modifier.fillMaxWidth()) {
                        Icon(when (title) { "应用" -> Icons.Default.Apps; "插件" -> Icons.Default.Extension; "操作" -> Icons.Default.TouchApp; "设置" -> Icons.Default.Settings; else -> Icons.Default.Widgets }, null)
                        Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
                    }
                } else items(items, key = { it.id }) { entry ->
                    val added = entry.id in existing
                    TextButton(onClick = feedbackClick { if (!added && entry.enabled) add(entry) }, enabled = !added && entry.enabled,
                        modifier = Modifier.fillMaxWidth()) {
                        OverlayShortcutIcon(entry); Spacer(Modifier.width(12.dp)); Text(entry.title, Modifier.weight(1f))
                        if (added) Icon(Icons.Default.Check, copy("overlay.picker.added"))
                        else if (!entry.enabled) Text(copy("overlay.picker.unavailable"), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if ((path.firstOrNull() == "应用" || searching) && loading) item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
                if ((path.firstOrNull() == "应用" || searching) && failed) item {
                    TextButton(onClick = feedbackClick { retry++ }) { Text(copy("overlay.picker.retry")) }
                }
            }
            EmptyContent(sections.isEmpty() && items.isEmpty() && !loading && !failed,
                if (searching) Icons.Default.Search else Icons.Default.Widgets,
                if (searching) copy("overlay.picker.empty.search") else copy("overlay.picker.empty"))
            }
        }
    }, confirmButton = { TextButton(onClick = feedbackClick(onDismiss)) { Text(copy("overlay.add.cancel")) } })
}
