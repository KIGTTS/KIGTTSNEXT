package com.kigtts.next.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.home.HomeScreen
import com.kigtts.next.ui.home.ObserveImeDismissal
import com.kigtts.next.ui.home.trackVoiceInputGesture
import com.kigtts.next.ui.navigation.*
import com.kigtts.next.ui.sketch.SketchState
import com.kigtts.plugin.api.PluginOutcome
import com.kigtts.next.ui.plugins.PluginRequests
import com.kigtts.next.ui.plugins.PluginExportRequest
import kotlinx.coroutines.launch

@Composable
fun KigttsApp(model: AppViewModel) {
    com.kigtts.next.ui.media.WebAndroidPermissionHost()
    com.kigtts.next.ui.media.RegisterBrowserInspectionRoot()
    com.kigtts.next.ui.media.PublishWebTheme()
    com.kigtts.next.ui.media.ObserveMediaPreviews(model.mediaRepository)
    val state by model.state.collectAsStateWithLifecycle()
    val phrases by model.phrases.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val snackbarAvoidance = remember { SnackbarAvoidance() }
    val toastContext = LocalContext.current.applicationContext
    var systemToast by remember { mutableStateOf<Toast?>(null) }
    DisposableEffect(Unit) { onDispose { systemToast?.cancel() } }
    val sketch = model.sketch
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sketchShare by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<PluginOutcome.Dialog?>(null) }
    var pluginMenu by remember { mutableStateOf<Pair<String, PluginOutcome.Menu>?>(null) }
    var pluginConfirmation by remember { mutableStateOf<Pair<String, PluginOutcome.Confirm>?>(null) }
    var pluginCapture by remember { mutableStateOf<Pair<String, PluginOutcome.CapturePhoto>?>(null) }
    var pluginInput by remember { mutableStateOf<Pair<String, PluginOutcome.TextInput>?>(null) }
    var pluginImport by remember { mutableStateOf<Pair<String, PluginOutcome.ImportResource>?>(null) }
    var pluginExport by remember { mutableStateOf<Pair<String, PluginOutcome.ExportResource>?>(null) }
    var packageInstall by remember { mutableStateOf<Pair<String, PluginOutcome.InstallPackage>?>(null) }
    val storeBootstrap by model.storeBootstrap.state.collectAsStateWithLifecycle()
    val speechPrompt by model.synthesisGuidance.prompt.collectAsStateWithLifecycle()
    val legacyPiperPrompt by model.legacyPiperGuidance.prompt.collectAsStateWithLifecycle()
    val modalWindows = remember { ModalWindows() }
    val inputOverlay = modalWindows.count > 0 || dialog != null || pluginMenu != null || pluginConfirmation != null || pluginInput != null || packageInstall != null || storeBootstrap != null || speechPrompt != null || legacyPiperPrompt != null
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
                is PluginOutcome.Message -> {
                    systemToast?.cancel()
                    systemToast = Toast.makeText(toastContext, outcome.text,
                        if (outcome.text.length > 60) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).also { it.show() }
                }
                is PluginOutcome.Dialog -> dialog = outcome
                is PluginOutcome.Menu -> pluginMenu = plugin to outcome
                is PluginOutcome.Confirm -> pluginConfirmation = plugin to outcome
                is PluginOutcome.CapturePhoto -> pluginCapture = plugin to outcome
                is PluginOutcome.TextInput -> pluginInput = plugin to outcome
                is PluginOutcome.ImportResource -> pluginImport = plugin to outcome
                is PluginOutcome.ExportResource -> pluginExport = plugin to outcome
                is PluginOutcome.InstallPackage -> packageInstall = plugin to outcome
                else -> Unit
            }
        }
    }
    val navigation = rememberPageNavigation(state)
    LaunchedEffect(navigation.current.identity) {
        focus.clearFocus()
        keyboard?.hide()
    }
    val back = rememberPredictiveReturn(
        (navigation.returningHome || state.voiceInputActive || state.webCanGoBack || state.subtitleExpanded || state.sidePage != null || state.panel != null || state.captionsVisible || state.destination != Destination.Home),
        identity = navigation.current.identity) {
        when {
            state.voiceInputActive -> model.endVoiceInput(false)
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
        com.kigtts.next.ui.cards.PhotoFactsConsentHost()
        ProvideButtonFeedback(state.hapticsEnabled) {
            CompositionLocalProvider(LocalSnackbarAvoidance provides snackbarAvoidance, LocalModalWindows provides modalWindows,
                com.kigtts.next.plugins.control.LocalAgentAccessPolicy provides model.agentAccess) {
            ProvideIconTextContextMenu {
            PluginRequests(model, pluginInput, pluginImport, { pluginInput = null }, { pluginImport = null },
                pluginCapture, { pluginCapture = null })
            PluginExportRequest(model, pluginExport) { pluginExport = null }
            com.kigtts.next.ui.plugins.PackageInstallRequest(model, packageInstall) { packageInstall = null }
            com.kigtts.next.ui.plugins.StoreBootstrapPrompt(model)
            com.kigtts.next.ui.plugins.SpeechServicePrompt(model)
            com.kigtts.next.ui.plugins.LegacyPiperPrompt(model)
                Scaffold(snackbarHost = { AppSnackbarHost(snackbar,snackbarAvoidance) },
                    contentWindowInsets = WindowInsets(0,0,0,0),
                    containerColor = MaterialTheme.colorScheme.background) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).then(if (state.destination is Destination.CardEditor) Modifier else Modifier.imePadding()).trackVoiceInputGesture(model.voiceInputGesture)) {
                        PageNavigationHost(navigation, state, model, sketch, sketchShare, { sketchShare = 0 }, { sketchShare++ },
                            if (navigation.current.scene != PageScene.Home) back.progress else 0f, back.edge, false) { active ->
                            HomeScreen(state.copy(destination = Destination.Home), phrases, categories, model, { model.navigate(Destination.Settings) },
                                if (active) back.progress else 0f, back.edge)
                        }
                        com.kigtts.next.ui.home.VoiceInputSurface(state, model)
                    }
                }
            com.kigtts.next.ui.cards.PolaroidPrintPreviewHost(model)
            com.kigtts.next.ui.plugins.PluginPermissionPrompts(model)
            pluginConfirmation?.let { (plugin, request) -> com.kigtts.next.ui.plugins.PluginConfirmation(request,
                { pluginConfirmation = null }, { pluginConfirmation = null; model.invoke(plugin, request.action.id) }) }
            dialog?.let { content -> MotionAlertDialog(onDismissRequest = { dialog = null }, title = { Text(content.title) }, text = { Text(content.text) },
                confirmButton = { TextButton(onClick = dialogFeedbackClick { dialog = null }) { Text("知道了") } }) }
            pluginMenu?.let { (plugin, content) ->
                MotionAlertDialog(
                    onDismissRequest = { pluginMenu = null }, title = { Text(content.title) },
                    text = {
                        Column {
                            content.items.forEach { item ->
                                TextButton(onClick = dialogFeedbackClick { pluginMenu = null; model.invoke(plugin, item.id) }) { Text(item.label) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = dialogFeedbackClick { pluginMenu = null }) { Text("关闭") } },
                )
            }
            }
            }
        }
    }
}
