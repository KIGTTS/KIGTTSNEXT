package com.kigtts.next.ui

import com.kigtts.next.i18n.uiText

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
import com.kigtts.next.ui.onboarding.FirstRunStore
import com.kigtts.next.ui.onboarding.HomeCoachMarks
import com.kigtts.next.ui.onboarding.NativeStartup
import com.kigtts.next.ui.settings.AppUpdateHost
import com.kigtts.next.ui.settings.LocalAppUpdates
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun KigttsApp(model: AppViewModel) = KigttsApp(model, null, null)

@Composable
internal fun KigttsApp(model: AppViewModel, onboarding: FirstRunStore?, startup: NativeStartup?) {
    com.kigtts.next.ui.media.WebAndroidPermissionHost()
    com.kigtts.next.ui.media.ObserveMediaPreviews(model.mediaRepository)
    val state by model.state.collectAsStateWithLifecycle()
    val renderer by model.subtitleRenderer.selection.collectAsStateWithLifecycle()
    val pageAppearance by model.pageAppearance.collectAsStateWithLifecycle()
    val webPresentation by model.webPresentation.collectAsStateWithLifecycle()
    val savedAppearance by model.savedAppearance.collectAsStateWithLifecycle()
    val baseAppearance = remember(state.theme, state.pageVersion, pageAppearance) {
        pageAppearance.filter { it.page == state.pageVersion }.fold(state.theme) { base, entry -> entry.appearance.applyTo(base) }
    }
    val rendererAppearance = if (state.destination == Destination.Home && state.sidePage == null)
        renderer?.appearance?.applyTo(baseAppearance) ?: baseAppearance else baseAppearance
    val visibleAppearance = webPresentation?.takeIf { it.matches(state) }?.appearance?.applyTo(rendererAppearance) ?: rendererAppearance
    val recovery by model.safeModeSession.state.collectAsStateWithLifecycle()
    val phrases by model.phrases.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val snackbarAvoidance = remember { SnackbarAvoidance() }
    val homePreview = remember { com.kigtts.next.ui.home.HomePreviewGeometry() }
    val toastContext = LocalContext.current.applicationContext
    var systemToast by remember { mutableStateOf<Toast?>(null) }
    DisposableEffect(Unit) { onDispose { systemToast?.cancel() } }
    val sketch = model.sketch
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sketchShare by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<PluginOutcome.Dialog?>(null) }
    var pluginMenu by remember { mutableStateOf<Pair<String, PluginOutcome.Menu>?>(null) }
    var artworkSelector by remember { mutableStateOf<Pair<String, PluginOutcome.SelectArtwork>?>(null) }
    var quickTextSelector by remember { mutableStateOf<Pair<String, PluginOutcome.SelectQuickText>?>(null) }
    var pluginControls by remember { mutableStateOf<Pair<String, PluginOutcome.Controls>?>(null) }
    var controlsRevision by remember { mutableIntStateOf(0) }
    var pluginConfirmation by remember { mutableStateOf<Pair<String, PluginOutcome.Confirm>?>(null) }
    var pluginCapture by remember { mutableStateOf<Pair<String, PluginOutcome.CapturePhoto>?>(null) }
    var pluginInput by remember { mutableStateOf<Pair<String, PluginOutcome.TextInput>?>(null) }
    var pluginImport by remember { mutableStateOf<Pair<String, PluginOutcome.ImportResource>?>(null) }
    var pluginExport by remember { mutableStateOf<Pair<String, PluginOutcome.ExportResource>?>(null) }
    var packageInstall by remember { mutableStateOf<Pair<String, PluginOutcome.InstallPackage>?>(null) }
    val profileShareFormat by model.sharing.profileFormat.collectAsStateWithLifecycle()
    val sharingSetup = if (com.kigtts.next.sharing.CLOUD_SHARING_ENABLED) model.sharing.setup.state.collectAsStateWithLifecycle().value else null
    val speechPrompt by model.synthesisGuidance.prompt.collectAsStateWithLifecycle()
    val legacyPiperPrompt by model.legacyPiperGuidance.prompt.collectAsStateWithLifecycle()
    val externalFile by model.externalFiles.prompt.collectAsStateWithLifecycle()
    val externalLink by model.externalLinks.pending.collectAsStateWithLifecycle()
    val resourceInstall by model.resourceInstalls.prompt.collectAsStateWithLifecycle()
    val modalWindows = remember { ModalWindows() }
    val inputOverlay = recovery.prompt || resourceInstall != null || externalLink != null || modalWindows.count > 0 || dialog != null || pluginMenu != null || quickTextSelector != null || artworkSelector != null || pluginControls != null || pluginConfirmation != null || pluginInput != null || packageInstall != null || sharingSetup != null || profileShareFormat != null || speechPrompt != null || legacyPiperPrompt != null || externalFile != null || savedAppearance != null
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
                is PluginOutcome.SelectArtwork -> artworkSelector = plugin to outcome
                is PluginOutcome.SelectQuickText -> quickTextSelector = plugin to outcome
                is PluginOutcome.Controls -> { pluginControls = plugin to outcome; controlsRevision++ }
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
        (navigation.returningHome || state.voiceInputActive || state.webCanGoBack || state.subtitleExpanded || state.quickTextEditing || state.sidePage != null || state.panel != null || state.captionsVisible || state.destination != Destination.Home),
        identity = navigation.current.identity, handoff = { pose ->
            val updated = model.state.value
            val parent = navigation.wanted.getOrNull(navigation.wanted.lastIndex - 1)
            val changedPage = updated.destination != state.destination || updated.navigationStack != state.navigationStack || updated.sidePage != state.sidePage
            if (changedPage && parent != null) navigation.retention.commitReturn(navigation.current, parent, pose.progress)
            changedPage && parent != null
        }) {
        when {
            state.voiceInputActive -> model.endVoiceInput(false)
            state.destination != Destination.Home -> model.navigateBack()
            navigation.returningHome -> Unit
            state.sidePage != null -> model.closeSidePage()
            state.quickTextEditing -> { focus.clearFocus(); keyboard?.hide(); model.finishQuickTextEditing() }
            state.mediaEditing -> { focus.clearFocus(); keyboard?.hide(); model.mediaEditing(false) }
            state.mediaId != null && state.fullscreen -> model.exitSubtitleFullscreen()
            state.webCanGoBack -> model.webBack()
            state.subtitleExpanded -> { focus.clearFocus(); keyboard?.hide(); model.exitSubtitleFullscreen() }
            state.panel != null -> model.closePanel()
            else -> model.dismissCaptionPanel()
        }
    }
    KigttsTheme(visibleAppearance, publishWebTheme = true) {
        val colors = MaterialTheme.colorScheme
        if (startup != null) SideEffect { startup.ready(colors) }
        com.kigtts.next.ui.cards.PhotoFactsConsentHost()
        ProvideButtonFeedback(state.hapticsEnabled) {
            CompositionLocalProvider(com.kigtts.next.ui.home.LocalHomePreviewGeometry provides homePreview,
                com.kigtts.next.i18n.LocalUiLocale provides state.uiLocale,
                com.kigtts.next.theme.LocalAppAppearanceSettings provides visibleAppearance,
                LocalAppUpdates provides model.appUpdates,
                LocalSnackbarAvoidance provides snackbarAvoidance, LocalModalWindows provides modalWindows,
                com.kigtts.next.ui.plugins.LocalPluginEntryBlocked provides inputOverlay,
                com.kigtts.next.sharing.LocalFaceShare provides com.kigtts.next.sharing.FaceShareActions(model.sharing::shareMedia,
                    if (com.kigtts.next.sharing.CLOUD_SHARING_ENABLED) model.sharing::openReceiving else null),
                com.kigtts.next.plugins.control.LocalAgentAccessPolicy provides model.agentAccess) {
            if (com.kigtts.next.sharing.CLOUD_SHARING_ENABLED) {
                com.kigtts.next.sharing.CloudShareLinkHost(model)
                com.kigtts.next.sharing.CloudSharingSetupHost(model)
                com.kigtts.next.sharing.CloudNativeHost(model)
            }
            if (profileShareFormat != null) com.kigtts.next.sharing.ProfileShareFormatDialog(model.sharing)
            CompositionLocalProvider(LocalIconTextMenuEnabled provides false) {
            HomeExitHandler(model.homeExit, state.destination == Destination.Home && !navigation.returningHome &&
                !state.voiceInputActive && !state.webCanGoBack && !state.subtitleExpanded && state.sidePage == null &&
                state.panel == null && !state.captionsVisible && !state.quickTextEditing && !inputOverlay, model::message)
            PluginRequests(model, pluginInput, pluginImport, { pluginInput = null }, { pluginImport = null },
                pluginCapture, { pluginCapture = null })
            com.kigtts.next.ui.plugins.PluginArtworkRequest(model, artworkSelector) { artworkSelector = null }
            PluginExportRequest(model, pluginExport) { pluginExport = null }
            com.kigtts.next.ui.plugins.PackageInstallRequest(model, packageInstall) { packageInstall = null }
            com.kigtts.next.ui.plugins.SpeechServicePrompt(model)
            com.kigtts.next.ui.home.SystemRecognitionMicPrompt(model)
            com.kigtts.next.ui.plugins.LegacyPiperPrompt(model)
            com.kigtts.next.ui.plugins.ExternalFilePrompt(model)
            com.kigtts.next.links.ExternalLinkHost(model)
            com.kigtts.next.resources.ResourceInstallHost(model)
            com.kigtts.next.overlay.OverlayEntrancePrompt(model)
            com.kigtts.next.recovery.SafeModePromptHost(model)
            if (onboarding != null) {
                AppUpdateHost(model.appUpdates, ready = state.destination == Destination.Home &&
                    state.sidePage == null && !state.editing && !state.quickTextEditing &&
                    state.mediaId == null && !state.voiceInputActive && !state.fullscreen &&
                    !state.subtitleExpanded && !navigation.returningHome && !inputOverlay) { url ->
                    model.externalLinks.offer(com.kigtts.next.links.ExternalLink(url = url,
                        mode = com.kigtts.next.links.WebOpenMode.CUSTOM_TAB))
                }
            }
                Scaffold(snackbarHost = { AppSnackbarHost(snackbar,snackbarAvoidance) },
                    contentWindowInsets = WindowInsets(0,0,0,0),
                    containerColor = MaterialTheme.colorScheme.background) { insets ->
                    val ownsIme = state.destination is Destination.CardEditor || state.destination.assistantChatHandlesIme()
                    Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).then(if (ownsIme) Modifier else Modifier.imePadding()).trackVoiceInputGesture(model.voiceInputGesture, model::dispatchVoiceGestureRelease)) {
                        PageNavigationHost(navigation, state, model, sketch, sketchShare, { sketchShare = 0 }, { sketchShare++ },
                            back, false) { active ->
                            val home: @Composable () -> Unit = {
                                HomeScreen(state.copy(destination = Destination.Home, theme = visibleAppearance), phrases, categories, model,
                                    { model.navigate(Destination.Settings) }, if (active) back else IdleReturnProgress,
                                    inlineTips = onboarding != null)
                            }
                            if (onboarding == null) home() else {
                                val returning by remember(back) { derivedStateOf { back.value.progress > 0f } }
                                HomeCoachMarks(progress = onboarding, sorting = model.quickSort,
                                    active = active && !inputOverlay && !returning && !state.voiceInputActive &&
                                        state.destination == Destination.Home && state.sidePage == null,
                                    subtitleEditing = state.editing,
                                    cursorAvailable = state.editing && !state.forcedFullscreen &&
                                        state.mediaId == null && state.editorText.isNotBlank(),
                                    quickDockCollapsed = state.panel == null && !state.subtitleExpanded,
                                    quickTextOpen = state.panel == HomePanel.QUICK_TEXT && !state.subtitleExpanded && !state.quickTextEditing,
                                    quickTextEditing = state.quickTextEditing, content = home)
                            }
                        }
                        if (!state.destination.assistantChatHandlesIme()) com.kigtts.next.ui.home.VoiceInputSurface(state, model)
                    }
                }
            com.kigtts.next.ui.cards.PolaroidPrintPreviewHost(model)
            com.kigtts.next.ui.plugins.PluginPermissionPrompts(model)
            savedAppearance?.let { request ->
                com.kigtts.next.ui.plugins.PluginAppearanceConfirmation(request) { model.answerSavedAppearance(request, it) }
            }
            pluginConfirmation?.let { (plugin, request) -> com.kigtts.next.ui.plugins.PluginConfirmation(request,
                { pluginConfirmation = null }, { pluginConfirmation = null; model.invoke(plugin, request.action.id) }) }
            pluginControls?.let { (plugin, request) ->
                val adjustsLed = renderer?.owner == plugin && renderer?.native is com.kigtts.plugin.api.NativeSubtitleRenderer.Led
                com.kigtts.next.ui.plugins.PluginControlsDialog(request,
                    { action, value -> model.invokeControl(plugin, action, value) }, { pluginControls = null },
                    controlsRevision, commitSlidersOnRelease = adjustsLed)
            }
            dialog?.let { content -> MotionAlertDialog(onDismissRequest = { dialog = null }, title = { Text(content.title) }, text = { Text(content.text) },
                confirmButton = { TextButton(onClick = dialogFeedbackClick { dialog = null }) { Text(uiText("plugin.stroke_done")) } }) }
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
                    confirmButton = { TextButton(onClick = dialogFeedbackClick { pluginMenu = null }) { Text(uiText("action.close")) } },
                )
            }
            quickTextSelector?.let { (plugin, request) ->
                val categoryIcons by model.quickTexts.categoryIcons.collectAsStateWithLifecycle()
                com.kigtts.next.ui.home.QuickTextSelectorDialog(
                    request = request,
                    phrases = phrases,
                    categories = categories,
                    categoryIcons = categoryIcons,
                    repository = model.mediaRepository,
                    onDismiss = {
                        quickTextSelector = null
                        request.cancelActionId?.let { action ->
                            scope.launch {
                                while (plugin in model.state.value.busyPlugins) kotlinx.coroutines.yield()
                                model.invoke(plugin, action, mapOf("quickTextIds" to "[]"))
                            }
                        }
                    },
                    onConfirm = { ids ->
                        quickTextSelector = null
                        scope.launch {
                            while (plugin in model.state.value.busyPlugins) kotlinx.coroutines.yield()
                            model.invoke(plugin, request.actionId, mapOf("quickTextIds" to JSONArray(ids).toString()))
                        }
                    },
                )
            }
            }
            }
        }
    }
}
