package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiCopy

import com.kigtts.next.i18n.uiText

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.management.managedItemShape
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
    var revealed by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<InstalledFont?>(null) }
    var weightEditor by remember { mutableStateOf<Pair<InstalledFont, FontWeightAxis>?>(null) }
    val lazy = rememberLazyListState()
    LaunchedEffect(lazy.isScrollInProgress) { if (lazy.isScrollInProgress) revealed = null }
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
        catch (e: Exception) { error = e.message ?: context.uiCopy("font.import_failed") }
        finally { busy = null }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { importFont(uri) }
    }
    LaunchedEffect(settings.font, revision) { locals = withContext(Dispatchers.IO) { installed.list(current) } }
    ReadablePage(840.dp) {
        LazyColumn(Modifier.fillMaxSize().testTag("font-settings-list"), state = lazy, contentPadding = com.kigtts.next.ui.navigation.pageContentPadding(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { FilledTonalButton(feedbackClick { if (busy == null) picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text(uiText("fonts.import"))
            } }
            fromStore?.let { open -> item {
                OutlinedButton(feedbackClick(open), Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(Icons.Default.Storefront, null); Spacer(Modifier.width(8.dp)); Text(uiText("fonts.get_store"))
                }
            } }
            if (busy != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Text(uiText("fonts.my_fonts"), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
            item { FontChoiceRow("system", "系统默认", settings.font == null,
                if (locals.isEmpty()) SettingPosition.SINGLE else SettingPosition.FIRST,
                { onChange(current.copy(font = null, fontName = "系统默认")) }) }
            items(locals, key = { it.key }) { font ->
                val selected = settings.font == font.key
                val preview = rememberFontListPreview(font.key, if (selected) settings.fontWeight else 400)
                val axis = preview?.axis
                    FontChoiceRow(font.key, font.name, selected,
                        if (font == locals.last()) SettingPosition.LAST else SettingPosition.MIDDLE,
                        {
                            if (current.font == font.key) axis?.let { weightEditor = font to it }
                            else onChange(current.copy(font = font.key, fontName = font.name))
                        },
                        revealed == font.key, { revealed = font.key }, { remove = font },
                        axis?.let { range -> ({ weightEditor = font to range }) }, variable = axis != null,
                        titleStyle = (preview?.face ?: AppearanceFont()).apply(MaterialTheme.typography.titleMedium), loaded = preview != null)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    weightEditor?.let { (font, axis) -> FontWeightDialog(font, axis, settings.fontWeight, { weightEditor = null }) { weight ->
        onChange(current.copy(font = font.key, fontName = font.name, fontWeight = weight))
        weightEditor = null
    } }
    remove?.let { font -> MotionAlertDialog(onDismissRequest = { remove = null }, title = { Text(uiText("font.delete")) },
        text = { Text(if (settings.font == font.key) uiText("font.delete_body") else font.name) },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick {
            remove = null
            scope.launch {
                busy = "delete"
                try {
                    withContext(Dispatchers.IO) { installed.remove(font.key) }
                    if (current.font == font.key) onChange(current.copy(font = null, fontName = "系统默认"))
                    revision++
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { error = failure.message ?: "字体未能删除，请重试" }
                finally { busy = null }
            }
        }) { Text(uiText("action.delete")) } }, dismissButton = { MotionTextButton(onClick = dialogFeedbackClick { remove = null }) { Text(uiText("action.cancel")) } }) }
}

@Composable private fun FontChoiceRow(id: String, name: String, selected: Boolean, position: SettingPosition, select: () -> Unit,
    active: Boolean = false, activate: () -> Unit = {}, remove: (() -> Unit)? = null,
    changeWeight: (() -> Unit)? = null, variable: Boolean = false, titleStyle: TextStyle? = null, loaded: Boolean = true) {
    val opacity by animateFloatAsState(if (loaded) 1f else 0f, tween(180), label = "font-row-load")
    SwipeRevealRow("font/$id", active, activate,
        endActions = listOfNotNull(changeWeight?.let { RevealAction("weight", "调整字重", Icons.Default.Tune) },
            remove?.let { RevealAction("delete", "删除字体", Icons.Default.Delete, true) }),
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = opacity }, enabled = loaded,
        invoke = { when (it) { "delete" -> remove?.invoke(); "weight" -> changeWeight?.invoke() } }) { revealed, toggle, close ->
    Surface(shape = managedItemShape(position, revealed), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().combinedClickable(enabled = loaded, onLongClick = if (remove != null) feedbackClick(toggle) else null,
            onClick = feedbackClick { if (revealed) close() else select() })
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = titleStyle ?: MaterialTheme.typography.titleMedium)
                    if (variable) Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(uiText("fonts.variable_weight"), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                RadioButton(selected, null)
                if (remove != null) MotionIconButton(onClick = feedbackClick(toggle), modifier = Modifier.size(40.dp).testTag("font-more/$id")) {
                    Icon(Icons.Default.MoreHoriz, "更多操作")
                }
            }
        }
    }
    }
}
