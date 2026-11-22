package com.kigtts.next.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.overlay.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.components.ModalPageTransition
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.rememberDialogClose
import com.kigtts.next.ui.plugins.settingPosition
import com.kigtts.next.ui.components.MotionAlertDialog as AlertDialog
import com.kigtts.next.i18n.rememberUiCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

private data class OverlayPickerPage(val path: List<String>, val searching: Boolean) {
    val depth get() = if (searching) 4 else path.size
}

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
        loading = true
        failed = false
        try {
            apps = OverlayAppCatalog.list(context.applicationContext)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) {
        val runtime = HostPluginRuntime.get(context.applicationContext as Application)
        while (true) { refreshOverlayPlugins(context, runtime.manager); delay(1500) }
    }
    val builtIns = remember(copy) { overlayBuiltInChoices().map { item -> item.copy(title = item.localizedTitle { copy(it) }) } }
    val categories = listOf("KIGTTS", "插件", "应用")
    fun sectionName(name: String) = when (name) {
        "插件" -> copy("overlay.category.plugins"); "应用" -> copy("overlay.category.apps")
        "页面" -> copy("overlay.category.pages"); "操作" -> copy("overlay.category.actions")
        "设置" -> copy("overlay.category.settings"); else -> name
    }
    val page = OverlayPickerPage(path, query.isNotBlank())
    var searchText by remember { mutableStateOf("") }
    fun goBack() {
        if (query.isNotBlank()) query = ""
        else if (path.isNotEmpty()) path = path.dropLast(1)
    }
    AlertDialog(onDismissRequest = onDismiss, onNavigateUp = if (page.depth > 0) ::goBack else null,
        title = { Text(copy("overlay.picker.title")) }, text = {
        val close = rememberDialogClose()
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it; if (it.isNotBlank()) searchText = it }, label = { Text(copy("overlay.picker.search")) },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("overlay-picker-search"))
            ModalPageTransition(page, OverlayPickerPage::depth, Modifier.weight(1f).fillMaxWidth()) { shown ->
                val level = shown.path
                val root = level.firstOrNull()
                val source = when (root) { "KIGTTS" -> builtIns; "插件" -> plugins; "应用" -> apps; else -> emptyList() }
                val scoped = if (level.size > 1) source.filter { it.category == level[1] } else source
                val items = if (shown.searching) (builtIns + plugins + apps).filter {
                    it.title.contains(searchText.trim(), true) || sectionName(it.category).contains(searchText.trim(), true)
                } else if (level.size > 2) scoped.filter { it.target.startsWith("page:") == (level[2] == "页面") } else scoped
                val sections = when {
                    shown.searching -> emptyList()
                    level.isEmpty() -> categories
                    level.size == 1 && root != "应用" -> source.map { it.category }.distinct()
                    level.size == 2 && root == "插件" -> listOf("页面", "操作").filter { section ->
                        scoped.any { it.target.startsWith("page:") == (section == "页面") }
                    }
                    else -> emptyList()
                }
                val loadingApps = (root == "应用" || shown.searching) && loading
                val appError = (root == "应用" || shown.searching) && failed
                Column(Modifier.fillMaxSize()) {
                    if (level.isNotEmpty() && !shown.searching) Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = feedbackClick { goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, copy("overlay.picker.back"))
                        }
                        Text(sectionName(level.last()), style = MaterialTheme.typography.titleSmall)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)) {
                            if (sections.isNotEmpty()) itemsIndexed(sections, key = { _, section -> "section:$section" }) { index, section ->
                                val icon = when (section) {
                                    "应用" -> Icons.Default.Apps
                                    "插件" -> Icons.Default.Extension
                                    "操作" -> Icons.Default.TouchApp
                                    "设置" -> Icons.Default.Settings
                                    else -> Icons.Default.Widgets
                                }
                                SettingItem(sectionName(section), "", icon, settingPosition(index, sections.size),
                                    onClick = { path = level + section }, trailing = { Icon(Icons.Default.ChevronRight, null) })
                            } else itemsIndexed(items, key = { _, item -> item.id }) { index, entry ->
                                val added = entry.id in existing
                                SettingItem(entry.title, if (entry.enabled) "" else copy("overlay.picker.unavailable"), null,
                                    settingPosition(index, items.size), onClick = { close { add(entry) } }, enabled = !added && entry.enabled,
                                    leading = { OverlayShortcutIcon(entry) },
                                    trailing = { if (added) Icon(Icons.Default.Check, copy("overlay.picker.added")) })
                            }
                            if (loadingApps) item {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                            if (appError) item {
                                SettingItem(copy("overlay.picker.retry"), "", Icons.Default.Refresh, SettingPosition.SINGLE, onClick = { retry++ })
                            }
                        }
                        EmptyContent(sections.isEmpty() && items.isEmpty() && !loadingApps && !appError,
                            if (shown.searching) Icons.Default.Search else Icons.Default.Widgets,
                            if (shown.searching) copy("overlay.picker.empty.search") else copy("overlay.picker.empty"))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dialogFeedbackClick(onDismiss)) { Text(copy("overlay.add.cancel")) } })
}
