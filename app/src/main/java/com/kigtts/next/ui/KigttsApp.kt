package com.kigtts.next.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.home.HomeScreen
import com.kigtts.next.ui.home.ObserveImeDismissal
import com.kigtts.next.ui.navigation.*
import com.kigtts.next.ui.sketch.SketchState
import com.kigtts.plugin.api.PluginOutcome
import com.kigtts.next.ui.plugins.PluginRequests
import com.kigtts.next.ui.plugins.PluginExportRequest
import kotlinx.coroutines.launch

@Composable
fun KigttsApp(model: AppViewModel, drawer: DrawerNavigation = rememberDrawerNavigation()) {
    com.kigtts.next.ui.media.ObserveMediaPreviews(model.mediaRepository)
    val state by model.state.collectAsStateWithLifecycle()
    val phrases by model.phrases.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sketch = remember { SketchState() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sketchShare by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<PluginOutcome.Dialog?>(null) }
    var pluginMenu by remember { mutableStateOf<Pair<String, PluginOutcome.Menu>?>(null) }
    var pluginInput by remember { mutableStateOf<Pair<String, PluginOutcome.TextInput>?>(null) }
    var pluginImport by remember { mutableStateOf<Pair<String, PluginOutcome.ImportResource>?>(null) }
    var pluginExport by remember { mutableStateOf<Pair<String, PluginOutcome.ExportResource>?>(null) }
    val inputOverlay = dialog != null || pluginMenu != null || pluginInput != null
    LaunchedEffect(state.mediaId) { if (state.mediaId != null) { focus.clearFocus(); keyboard?.hide() } }
    var restoreInput by remember { mutableStateOf(false) }
    ObserveImeDismissal(state.editing, inputOverlay) { model.editing(false) }
    ObserveImeDismissal(state.mediaEditing, inputOverlay) { model.mediaEditing(false) }
    LaunchedEffect(inputOverlay, state.editing) {
        if (inputOverlay && state.editing) restoreInput = true
        else if (!inputOverlay && restoreInput) {
            restoreInput = false
            if (state.editing) keyboard?.show()
        }
    }
    LaunchedEffect(model) {
        model.events.collect { (plugin, outcome) ->
            when (outcome) {
                is PluginOutcome.Message -> scope.launch { snackbar.showSnackbar(outcome.text) }
                is PluginOutcome.Dialog -> dialog = outcome
                is PluginOutcome.Menu -> pluginMenu = plugin to outcome
                is PluginOutcome.TextInput -> pluginInput = plugin to outcome
                is PluginOutcome.ImportResource -> pluginImport = plugin to outcome
                is PluginOutcome.ExportResource -> pluginExport = plugin to outcome
                else -> Unit
            }
        }
    }
    val drawerActive = drawer.active
    val navigation = rememberPageNavigation(state)
    LaunchedEffect(navigation.current.identity) {
        focus.clearFocus()
        keyboard?.hide()
    }
    val back = rememberPredictiveReturn(!drawerActive &&
        (navigation.returningHome || state.webCanGoBack || state.subtitleExpanded || state.sidePage != null || state.panel != null || state.captionsVisible || state.destination != Destination.Home),
        identity = navigation.current.identity) {
        when {
            // Back can arrive before recomposition updates callback enablement during opening.
            drawer.active -> drawer.close()
            state.destination != Destination.Home -> model.navigateBack()
            navigation.returningHome -> Unit
            state.sidePage != null -> model.closeSidePage()
            state.quickTextEditing -> { focus.clearFocus(); keyboard?.hide(); model.finishQuickTextEditing() }
            state.mediaEditing -> { focus.clearFocus(); keyboard?.hide(); model.mediaEditing(false) }
            state.webCanGoBack -> model.webBack()
            state.subtitleExpanded -> { focus.clearFocus(); keyboard?.hide(); model.exitSubtitleFullscreen() }
            state.panel != null -> model.closePanel()
            else -> model.closeListeningPanel()
        }
    }
    KigttsTheme(state.theme) {
        ProvideButtonFeedback(state.hapticsEnabled) {
            PluginRequests(model, pluginInput, pluginImport, { pluginInput = null }, { pluginImport = null })
            PluginExportRequest(model, pluginExport) { pluginExport = null }
            RightNavigationDrawer(drawer, drawerContent = { direction -> AppDrawer(state, model, drawer, direction) }) {
                Scaffold(snackbarHost = { SnackbarHost(snackbar,Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal))) },
                    contentWindowInsets = WindowInsets(0,0,0,0),
                    containerColor = MaterialTheme.colorScheme.background) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                        PageNavigationHost(navigation, state, model, sketch, sketchShare, { sketchShare = 0 }, { sketchShare++ },
                            if (navigation.current.scene != PageScene.Home) back.progress else 0f, back.edge, drawerActive) { active ->
                            HomeScreen(state.copy(destination = Destination.Home), phrases, categories, model, drawer::open,
                                if (active) back.progress else 0f, back.edge)
                        }
                    }
                }
            }
            dialog?.let { content -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(content.title) }, text = { Text(content.text) },
                confirmButton = { TextButton(onClick = feedbackClick { dialog = null }) { Text("知道了") } }) }
            pluginMenu?.let { (plugin, content) ->
                AlertDialog(
                    onDismissRequest = { pluginMenu = null }, title = { Text(content.title) },
                    text = {
                        Column {
                            content.items.forEach { item ->
                                TextButton(onClick = feedbackClick { pluginMenu = null; model.invoke(plugin, item.id) }) { Text(item.label) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = feedbackClick { pluginMenu = null }) { Text("关闭") } },
                )
            }
        }
    }
}
