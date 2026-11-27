package com.kigtts.next.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.ThemeSettingsStore
import com.kigtts.next.theme.PageAppearance
import com.kigtts.next.theme.SavedAppearanceRequest
import com.kigtts.next.theme.resolveAppearance
import com.kigtts.plugin.api.*
import kotlinx.coroutines.CancellationException
import com.kigtts.next.audio.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import android.icu.text.BreakIterator
import androidx.core.content.edit
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.plugins.control.hideBundledEntries
import com.kigtts.next.quickactions.*
import androidx.compose.ui.geometry.Offset

class AppViewModel @JvmOverloads constructor(application: Application, engineFixtures: List<KigPlugin> = emptyList(),
    audio: AudioPlatform = AndroidAudioPlatform(application)) : AndroidViewModel(application) {
    init { com.kigtts.next.ui.components.MaterialSymbols.initialize(application) }
    val pluginChatHistory = com.kigtts.next.ui.plugins.ChatHistoryCache()
    val sketch = com.kigtts.next.ui.sketch.SketchState()
    private val hostRuntime = if (engineFixtures.isEmpty()) HostPluginRuntime.get(application) else null
    val pluginManager = hostRuntime?.manager ?: PluginManager(application, engineFixtures,
        com.kigtts.next.sharing.pausedSharingPlugins + LEGACY_GESTURE_PLUGIN)
    private val recovery = hostRuntime?.recovery ?: com.kigtts.next.recovery.CrashRecovery(application)
    internal val safeModeSession = com.kigtts.next.recovery.SafeModeSession(recovery, ::refreshPlugins)
    init { pluginManager.safeMode = recovery::safeMode }
    val registry = pluginManager.registry
    internal val editorWorkspace by lazy { com.kigtts.next.plugins.editor.EditorWorkspace(getApplication(), pluginManager) }
    val readingCacheBytes get() = hostRuntime?.reading?.cacheBytes
    val readingBakeProgress get() = hostRuntime?.reading?.bakeProgress
    val audioDevices = (audio as? AndroidAudioPlatform)?.devices ?: AudioDeviceRouting(application)
    fun pluginPage(pluginId: String, pageId: String): PluginPage? = registry.plugin(pluginId)?.let { plugin ->
        val page = audioDevices.decorate(plugin, plugin.page(pageId, context()))
        when {
            pluginId == com.kigtts.next.plugins.control.STORE_ID -> page?.hideBundledEntries(pluginManager.bundledIds + LEGACY_GESTURE_PLUGIN)
            pluginId == PLUGIN_EDITOR_ID && pageId == EDITOR_MANAGER_PAGE && page != null -> editorWorkspace(
                page, registry.plugin(NATIVE_BUILDER_ID)?.takeIf {
                    pluginManager.status.any { status -> status.installed.manifest.id == NATIVE_BUILDER_ID &&
                        status.installed.publisherSha256 == com.kigtts.next.plugins.control.STORE_PUBLISHER }
                }?.page(EDITOR_MANAGER_PAGE, context()), hostCopy("plugin.editor_projects"), hostCopy("editor.signatures"))
            else -> page
        }
    }
    private val engineSelection = application.getSharedPreferences("engine-selection", android.content.Context.MODE_PRIVATE)
    val selectedTtsId get() = engineSelection.getString("tts", null)
    val selectedAsrId get() = engineSelection.getString("asr", null)
    private val managementOrder = ManagementOrderStore(application)
    fun pluginOrder(ids: List<String>) = managementOrder.ordered("plugins", ids)
    fun reorderPlugins(ids: List<String>) {
        val current = (pluginManager.status.map { it.installed.manifest.id } + registry.plugins.map { it.descriptor.id } + registry.plugins.flatMap { runtime -> runtime.contributions.managedPackages.map { "${runtime.descriptor.id}::${it.id}" } }).distinct()
        managementOrder.save("plugins", ids, current)
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
    }
    private val themeStore = ThemeSettingsStore(application)
    internal val pageAppearance = MutableStateFlow<List<PageAppearance>>(emptyList())
    internal val savedAppearance = MutableStateFlow<SavedAppearanceRequest?>(null)
    internal val homeChrome = MutableStateFlow<Map<KigPlugin, HomeChromePresentation>>(emptyMap())
    private val placeholderStore = SubtitlePlaceholderStore(application)
    private val subtitleAppearanceStore = SubtitleAppearanceStore(application)
    private val feedbackStore = FeedbackSettingsStore(application)
    val quickSort = QuickSortPreference(application)
    val homeExit = HomeExitPreference(application)
    internal val subtitleRenderer = SubtitleRendererState()
    internal val listeningPanel = ListeningPanelState()
    private val phraseStore = QuickPhraseStore(application)
    private val panelStore = HomePanelStore(application)
    private val subtitleSizeStore = SubtitleSizeStore(application)
    val agentAccess = com.kigtts.next.plugins.control.AgentAccessPolicy(application) { pluginManager.permissionOwner(it) }
    init { pluginManager.automaticPermission = { owner, _ -> agentAccess.full(owner) } }
    private val experiments = application.getSharedPreferences("experiments", 0)
    private val playbackPreferences = application.getSharedPreferences("playback", 0)
    private val presentationPreferences = application.getSharedPreferences("media-presentation", 0)
    val mediaRepository = hostRuntime?.mediaRepository ?: MediaRepository(application, pluginManager.paths)
    val stickers = hostRuntime?.stickers ?: com.kigtts.next.cards.StickerRepository(application, pluginManager.paths, mediaRepository)
    val cards = hostRuntime?.cards ?: com.kigtts.next.cards.CardRepository(application, stickers)
    val identities = com.kigtts.next.identity.IdentityRepository(application, cards)
    internal val contactFavorites by lazy { com.kigtts.next.identity.ContactFavorites.get(application) }
    init { com.kigtts.next.identity.warmIdentityBackground(viewModelScope, stickers, identities) }
    val cardMotions = com.kigtts.next.cards.CardMotionRepository(stickers)
    val cardUi = CardWorkspace(::navigate, { navigate(Destination.Cards) },
        { cards.albums.value }, { id -> cards.cards.value.any { it.id == id } }, { id -> !cards.isReadOnly(id) })
    val stickerPlugins = hostRuntime?.stickerPlugins ?: com.kigtts.next.plugins.stickers.StickerHost(stickers, pluginManager.paths,
        { target, id -> cardUi.open(target, id) }, { state.value.editingCardId() }, { owner -> agentAccess.consume(owner) })
    init { if (hostRuntime == null) pluginManager.stickerBrokerFactory = stickerPlugins::forPlugin }
    val mediaPlayback = com.kigtts.next.ui.media.MediaPlaybackSessions()
    private val audioSessionDelegate = lazy { AudioCardSession(application) }
    val cardAudio get() = audioSessionDelegate.value
    private val uiLanguage = com.kigtts.next.i18n.UiLanguageStore(application)
    private val initialUiLanguage = uiLanguage.selection()
    private val initialUiLocale = uiLanguage.load(initialUiLanguage)
    private val captionFonts = CaptionFontSettings(application)
    private val mutableState = MutableStateFlow(AppState(uiLocale = initialUiLocale,
        followsSystemUiLanguage = initialUiLanguage == com.kigtts.next.i18n.UiLanguage.SYSTEM,
        theme = themeStore.load(), subtitlePlaceholder = subtitlePlaceholder(initialUiLocale), hapticsEnabled = feedbackStore.load(),
        mediaQuickTextCollapse = feedbackStore.loadMediaCollapse(),
        subtitleAppearance = subtitleAppearanceStore.load(),
        captionTextSize = captionFonts.panel(),
        lastExpandedPanelHeight = panelStore.loadHeight(), panelColumns = panelStore.loadColumns(), subtitleSizePercent = subtitleSizeStore.load(),
        appleLivePhotos = experiments.getBoolean("apple-live-photos", false),
        autoPlayback = playbackPreferences.getBoolean("automatic", false),
        contentAutoPlay = presentationPreferences.getBoolean("autoPlay", true),
        contentAudioMode = com.kigtts.next.media.MediaAudioMode.read(presentationPreferences.getString("audioMode", null))))
    val state = mutableState.asStateFlow()
    internal val quickActionPreferences = QuickActionPreferences(application)
    internal val subtitleGestures = SubtitleGestureStore(application)
    private val bluetoothSubtitles = lazy { BluetoothSubtitleOutput(application) }
    private var lastBluetoothSubtitle = ""
    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                subtitleGestures.migrate(pluginManager.paths.storage(LEGACY_GESTURE_PLUGIN).file("config/gestures.properties"))
            }
        }
        viewModelScope.launch {
            state.map { current ->
                when {
                    current.mediaId != null || current.audioId != null -> ""
                    current.editing -> null
                    else -> current.editorText
                }
            }.distinctUntilChanged().filterNotNull().collect(::publishBluetoothSubtitle)
        }
    }

    internal fun setBluetoothSubtitles(enabled: Boolean) {
        quickActionPreferences.bluetoothSubtitles = enabled
        if (enabled) publishBluetoothSubtitle(lastBluetoothSubtitle)
        else if (bluetoothSubtitles.isInitialized()) bluetoothSubtitles.value.setEnabled(false)
    }

    private fun publishBluetoothSubtitle(text: String) {
        lastBluetoothSubtitle = text
        if (quickActionPreferences.bluetoothSubtitles) {
            bluetoothSubtitles.value.update(text)
            bluetoothSubtitles.value.setEnabled(true)
        }
    }

    internal fun submitSubtitleStroke(points: List<Offset>, duration: Long, width: Int, height: Int, minimumSpan: Float) {
        val settings = subtitleGestures.state.value
        val invocation = state.value.pageVersion
        viewModelScope.launch {
            val gesture = if (settings.active && state.value.mediaId == null && state.value.audioId == null)
                withContext(Dispatchers.Default) { matchSubtitleGesture(points, minimumSpan) } else null
            if (state.value.pageVersion != invocation || state.value.editing) return@launch
            val currentSettings = subtitleGestures.state.value
            val entry = gesture?.let(settings.bindings::get)?.takeIf { it.enabled && currentSettings == settings }
            val phrase = entry?.let { binding -> phrases.value.firstOrNull { it.id == binding.phraseId } }
            if (phrase != null) {
                content.send(phrase)
                return@launch
            }
            val target = registry.gestureActions(PluginGesture.SUBTITLE_STROKE, context()).firstOrNull() ?: return@launch
            val json = org.json.JSONArray().also { rows ->
                points.forEach { rows.put(org.json.JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
            }
            invoke(target.plugin.descriptor.id, target.action.id, mapOf("gesture.points" to json.toString(),
                "gesture.durationMs" to duration.toString(), "gesture.width" to width.toString(), "gesture.height" to height.toString()))
        }
    }
    init { audioDevices.onChanged = { update { it.copy(pluginRevision = it.pluginRevision + 1) } } }
    val cameraSaves = CameraSaveQueue(cards, stickers, viewModelScope, ::message, saveMotion = { imageId, clip ->
        cardMotions.import(imageId, clip.video, clip.durationMs, clip.rotationDegrees, clip.shutterPositionMs, clip.colorMatrix, clip.lut, clip.lutStrength, clip.effects)
    })
    fun saveCameraVideo(file: java.io.File, timeLapse: Boolean, onSaved: (android.net.Uri) -> Unit = {}) { viewModelScope.launch {
        try { onSaved(com.kigtts.next.media.saveCameraVideo(getApplication(), file, timeLapse)) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message(hostCopy("camera.video_save_failed_original")) }
    } }
    val cardCapture = CardCaptureCoordinator(cards, stickers, viewModelScope, { state.value }, ::navigate, ::navigateBack, ::message,
        { cardUi.selectedAlbum.value }, { id -> cardUi.open(StickerUiTarget.CARDS, id) },
        { id -> cardUi.focusCard(id, openPreview = false) }, saveMotion = { imageId, clip ->
            cardMotions.import(imageId, clip.video, clip.durationMs, clip.rotationDegrees, clip.shutterPositionMs, clip.colorMatrix, clip.lut, clip.lutStrength, clip.effects)
        }, useSystemCamera = { application.getSharedPreferences("polaroid_camera", 0).getBoolean("use-system-camera", false) })
    val quickTexts = hostRuntime?.quickTexts ?: QuickTextLibrary(phraseStore)
    val content = ContentWorkspace(application, mediaRepository, quickTexts, { state.value }, ::update, ::navigate, ::navigateBack, ::text,
        ::playAudioCard, { if(audioSessionDelegate.isInitialized())cardAudio.clear();viewModelScope.launch { speech.stopSpeaking() } }, ::autoRead, permissions=pluginManager.permissions,
        openCustomTab = { url, title -> externalLinks.offer(com.kigtts.next.links.ExternalLink(url = url, title = title, mode = com.kigtts.next.links.WebOpenMode.CUSTOM_TAB)) })
    val phrases = quickTexts.phrases
    val categories = quickTexts.categories
    internal val quickTextBackup by lazy { QuickTextBackup(this) }

    internal fun installQuickTextPresets(selected: Set<String>, firstRun: Boolean = false) {
        val presets = localizedQuickTextPresets { hostCopy(it) }.filter { it.id in selected }
        val first = quickTexts.appendPresets(presets, initialGroup = hostCopy("quick_presets.general.name").takeIf { firstRun })
        if (first != null && state.value.selectedCategory !in categories.value) category(first)
    }
    internal val backupRestore by lazy { com.kigtts.next.backup.BackupRestore(this) }
    internal val sharing by lazy { com.kigtts.next.sharing.SharingCoordinator(this, viewModelScope) }
    internal val legacyImports = com.kigtts.next.legacy.LegacyImportController(
        application, quickTexts, mediaRepository, viewModelScope, ::message, ::reloadBackedUpSettings,
        restoreVoices = { content ->
            val piper = registry.plugin("engine.piper")
            if (piper != null && registry.available(piper) && piper.voiceManager != null) {
                val packs = com.kigtts.next.legacy.legacyPiperPackages(content).iterator()
                while (true) {
                    val archive = withContext(Dispatchers.IO) { if (packs.hasNext()) packs.next() else null } ?: break
                    try { externalFiles.importVoice(archive, ExternalFileType.PIPER_VOICE) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { message(hostCopy("backup.legacy_voice_failed")) }
                    finally { withContext(NonCancellable + Dispatchers.IO) { archive.delete() } }
                }
            }
        },
    )
    val quickTextPlugins=hostRuntime?.quickTextPlugins ?: com.kigtts.next.plugins.quicktext.QuickTextHost(quickTexts,mediaRepository,pluginManager.paths) {this}
    init {if(hostRuntime==null)pluginManager.quickTextBrokerFactory=quickTextPlugins::forPlugin}
    private val appPlugins = hostRuntime?.appPlugins ?: com.kigtts.next.plugins.control.AppControlHost(pluginManager) { this }
    init { if (hostRuntime == null) pluginManager.appBrokerFactory = appPlugins::forPlugin }
    init { if (state.value.selectedCategory !in categories.value) category(categories.value.firstOrNull() ?: "常用") }
    private val outcomes = Channel<Pair<String, PluginOutcome>>(Channel.BUFFERED)
    val events = outcomes.receiveAsFlow()
    init { legacyImports.migrateOriginalInstallation() }
    internal val systemRecognitionPermission = MutableStateFlow<com.kigtts.engines.recognitionservice.SystemRecognitionPermissionIssue?>(null)
    private val captionMicrophone = if (audio is AndroidAudioPlatform) RecognitionForegroundService.Microphone(application) else null
    private val speech = SpeechCoordinator(viewModelScope, registry, audio, { state.value }, ::update, ::message, ::text,
        selection = { if (it) selectedTtsId else selectedAsrId }, automaticRead = ::setAutoPlayback,
        systemPermissionDenied = { systemRecognitionPermission.value = it },
        copy = { key, _ -> hostCopy(key) },
        captionMicrophone = captionMicrophone)
    private val captionOutput = CaptionOutputCoordinator(application, viewModelScope, state, speech::start, speech::closeCaptions,
        ::message, ::readyCaptionService, captionMicrophone)
    val overlayRequests = captionOutput.permissionRequests
    fun overlayResult() = captionOutput.permissionResult()
    val captionServiceReady get() = readyCaptionService() != null
    private fun readyCaptionService(): String? {
        val selected = selectedAsrId
        return registry.plugins.firstOrNull { plugin ->
            (selected == null || selected == plugin.descriptor.id) &&
                plugin.asr?.info?.let { it.role == AsrRole.SERVICE && it.availability == EngineAvailability.READY } == true
        }?.descriptor?.id
    }
    internal fun hostCopy(key: String, vararg values: Pair<String, String>) = getApplication<Application>().uiCopy(key, *values, locale = state.value.uiLocale)

    fun openCaptionWindow() { if (captionServiceReady) captionOutput.openWindow() else message(hostCopy("listening.engine_setup")) }
    fun resumeCaptionWindow() {
        speech.onForeground()
        captionOutput.resume()
    }
    fun dismissCaptionPanel() {
        val floatingOwnsSession = state.value.recognitionOutput != CaptionOutput.PANEL && captionOutput.windowOpen()
        if (floatingOwnsSession) update { it.copy(captionsVisible = false) } else speech.closeCaptions()
    }
    fun toggleCaptionPanel(owner: String) {
        val current = state.value
        if (current.captionsVisible || (current.listening && !current.voiceInputActive)) {
            speech.closeCaptions()
            return
        }
        invoke(owner, "listen")
    }
    val voiceInputGesture = VoiceInputGestureState()
    internal fun startVoiceInputFromDock(action: RegisteredGesture) {
        val request = voiceInputGesture.arm()
        invokeWithResult(action.plugin.descriptor.id, action.action.id, emptyMap()) { accepted ->
            if (!accepted) voiceInputGesture.reject(request)
        }
    }
    internal fun dispatchVoiceGestureRelease() {
        if (!state.value.voiceInputActive) return
        val target = voiceInputGesture.consumeRelease() ?: return
        if (state.value.continuousRecognition) return
        when (target) {
            VoiceReleaseTarget.SEND -> endVoiceInput(true)
            VoiceReleaseTarget.READ -> endVoiceInput(true, read = true)
            VoiceReleaseTarget.CANCEL -> endVoiceInput(false)
            VoiceReleaseTarget.CONTINUOUS -> continueVoiceInput()
            VoiceReleaseTarget.PLUGIN -> {
                val id = voiceInputGesture.consumePluginRelease()
                val action = registry.actions(UiSlot.VOICE_INPUT_RELEASE, context()).firstOrNull {
                    it.plugin.descriptor.id + "/" + it.action.id == id
                }
                val voiceText = state.value.voiceInputText
                endVoiceInput(false)
                action?.let { invoke(it.plugin.descriptor.id, it.action.id, mapOf("voiceText" to voiceText)) }
            }
        }
    }
    fun endVoiceInput(submit: Boolean = false, read: Boolean = false) = speech.end(submit, read)
    internal fun startChatVoiceInput() = speech.chatInput()
    fun continueVoiceInput() = speech.continueInput()
    val microphoneRequests = speech.permissionRequests
    fun microphoneResult(granted: Boolean) {
        if (!granted) voiceInputGesture.discardRelease()
        speech.permissionResult(granted)
    }
    // Every callback dependency must exist before bootstrap can resume from background I/O.
    private val navigationHistory = ArrayDeque<Destination>()
    internal var pluginCatalogReady = engineFixtures.isNotEmpty()
    internal val synthesisGuidance = SpeechServiceGuidance(application, status = {
        when {
            registry.plugins.any { it.tts?.info?.role == TtsRole.SERVICE && registry.available(it) } -> SpeechServiceStatus.ENABLED
            pluginManager.status.any { "speech-service" in it.installed.manifest.capabilities } -> SpeechServiceStatus.UNAVAILABLE
            else -> SpeechServiceStatus.MISSING
        }
    }, install = { openSynthesisStore() }, manage = { navigate(Destination.Plugins) })
    private fun openSynthesisStore() {
        openSpeechPluginStore("语音合成服务")
    }
    internal fun openExpressionStore() {
        invokeWithResult(com.kigtts.next.plugins.control.STORE_ID, "browse-expressions", emptyMap()) { accepted ->
            if (!accepted) message(hostCopy("market.unavailable"))
        }
    }

    internal fun openSpeechPluginStore(query: String) {
        val market = com.kigtts.next.plugins.control.STORE_ID
        invokeWithResult(market, "search-save", mapOf("value" to query)) { accepted ->
            if (accepted) navigate(Destination.PluginPage(market, "search"))
            else message(hostCopy("market.unavailable"))
        }
    }
    internal val legacyPiperGuidance = LegacyPiperGuidance(application,
        hostRuntime?.legacyPiperStartupChecked ?: java.util.concurrent.atomic.AtomicBoolean(false),
        hasPacks = { LegacyPiperPacks.exists(application.filesDir, pluginManager.paths.data) },
        missing = { buildSet {
            if (registry.plugins.none { it.tts?.info?.role == TtsRole.SERVICE }) add(MissingPiperComponent.SERVICE)
            if (pluginManager.status.none { it.installed.manifest.id == "engine.piper" }) add(MissingPiperComponent.ENGINE)
        } }, install = ::openSpeechPluginStore)
    private val sourceLinks: com.kigtts.next.plugins.control.SourceLinkCoordinator by lazy { com.kigtts.next.plugins.control.SourceLinkCoordinator(application, registry,
        ::invokeWithResult, ::message, available = { it !in state.value.busyPlugins },
        ready = { pluginCatalogReady }, missingStore = { message(hostCopy("market.unavailable")) }) }
    internal val externalFiles by lazy { ExternalFileOpen(this, viewModelScope) }
    val externalLinks by lazy { com.kigtts.next.links.ExternalLinks(this) }
    val resourceInstalls by lazy { com.kigtts.next.resources.ResourceInstalls(this, viewModelScope) }
    init { if (engineFixtures.isEmpty()) viewModelScope.launch { refreshPlugins() } }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observePluginContext() = viewModelScope.launch {
        state.map { context() }
            .distinctUntilChanged().debounce(100).collect { context ->
                if (pluginManager.refreshContext(context)) update { it.copy(pluginRevision = it.pluginRevision + 1) }
            }
    }
    init { if (engineFixtures.isEmpty()) observePluginContext() }
    init { viewModelScope.launch {
        pluginManager.uiChanges.collect { id ->
            try { if (pluginManager.refreshUi(id, context())) update { it.copy(pluginRevision = it.pluginRevision + 1) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Failed workers remain handled by plugin management. */ }
        }
    } }
    init {
        registry.plugin(com.kigtts.next.plugins.control.STORE_ID)?.let { market ->
            viewModelScope.launch {
                market.uiChanges.collect { update { it.copy(pluginRevision = it.pluginRevision + 1) } }
            }
        }
    }
    init { hostRuntime?.let { host -> host.attach(this); viewModelScope.launch { host.revision.drop(1).collect { refreshPlugins() } } } }
    fun sourceLink(uri: android.net.Uri?) = sourceLinks.accept(uri)

    fun externalIntent(intent: android.content.Intent?) { if (sharing.accept(intent)) return; externalFiles.accept(intent); externalLinks.accept(intent) }

    private fun update(transform: (AppState) -> AppState) {
        mutableState.update { previous ->
            val next = transform(previous)
            if (next.destination != previous.destination || next.sidePage != previous.sidePage ||
                next.navigationStack != previous.navigationStack) next.copy(pageVersion = previous.pageVersion + 1)
            else next
        }
        val page = state.value.pageVersion
        if (pageAppearance.value.any { it.page != page }) pageAppearance.update { patches -> patches.filter { it.page == page } }
        cardCapture.onDestinationChanged(state.value.destination)
        content.onDestinationChanged(state.value.destination)
    }
    fun context() = state.value.let { PluginContext(it.editorText, it.transcript, it.listening, it.captionsVisible,
        inputs = mapOf("host.subtitleRenderer" to subtitleRenderer.selection.value?.owner.orEmpty(),
            "host.listeningPanel" to listeningPanel.selection.value?.owner.orEmpty(),
            "host.subtitleFullscreen" to (it.subtitleExpanded || it.forcedFullscreen).toString()),
        contentKind = mediaRepository.get(it.mediaId)?.kind?.name ?: "TEXT", contentId = it.mediaId,
        automaticRead = it.autoPlayback, speechActive = it.speaking,
        ttsPluginId = registry.plugin(selectedTtsId.orEmpty())?.takeIf { plugin -> plugin.tts != null }?.descriptor?.id
            ?: registry.plugins.firstOrNull { plugin -> plugin.tts != null }?.descriptor?.id,
        voiceInputActive = it.voiceInputActive, continuousRecognition = it.continuousRecognition,
        recognitionServiceId = selectedAsrId, locale = it.uiLocale) }
    internal fun setUiLanguage(language: com.kigtts.next.i18n.UiLanguage) {
        if (uiLanguage.selection() != language) uiLanguage.save(language)
        refreshUiLanguage()
    }
    internal fun refreshUiLanguage() {
        val selection = uiLanguage.selection()
        val locale = uiLanguage.load(selection)
        val followsSystem = selection == com.kigtts.next.i18n.UiLanguage.SYSTEM
        val current = state.value
        if (current.uiLocale == locale && current.followsSystemUiLanguage == followsSystem) return
        update { it.copy(uiLocale = locale, followsSystemUiLanguage = followsSystem, subtitlePlaceholder = subtitlePlaceholder(locale)) }
        if (quickActionPreferences.lockScreenShortcut) LockScreenShortcut.sync(getApplication<Application>())
    }
    internal fun reloadBackedUpSettings() {
        subtitleGestures.reload()
        setBluetoothSubtitles(quickActionPreferences.bluetoothSubtitles)
        LockScreenShortcut.sync(getApplication<Application>())
        refreshUiLanguage()
        quickSort.reload()
        homeExit.reload()
        val application = getApplication<Application>()
        com.kigtts.next.overlay.OverlayStore.get(application).reload()
        com.kigtts.next.overlay.NativeOverlayService.sync(application)
        update {
            it.copy(theme = themeStore.load(), subtitlePlaceholder = subtitlePlaceholder(it.uiLocale),
                hapticsEnabled = feedbackStore.load(), subtitleAppearance = subtitleAppearanceStore.load(),
                mediaQuickTextCollapse = feedbackStore.loadMediaCollapse(),
                captionTextSize = captionFonts.panel(), lastExpandedPanelHeight = panelStore.loadHeight(),
                panelColumns = panelStore.loadColumns(), subtitleSizePercent = subtitleSizeStore.load(),
                appleLivePhotos = experiments.getBoolean("apple-live-photos", false),
                autoPlayback = playbackPreferences.getBoolean("automatic", false),
                contentAutoPlay = presentationPreferences.getBoolean("autoPlay", true),
                contentAudioMode = com.kigtts.next.media.MediaAudioMode.read(presentationPreferences.getString("audioMode", null)),
                pluginRevision = it.pluginRevision + 1)
        }
    }
    internal suspend fun reloadBackedUpSpeechSettings() {
        hostRuntime?.reloadBackedUpSpeechSettings()
    }
    private fun subtitlePlaceholder(locale: String) =
        placeholderStore.load(getApplication<Application>().uiCopy("subtitle.placeholder", locale = locale))
    fun navigate(requested: Destination) {
        val destination = if (requested == Destination.PluginPage(NATIVE_BUILDER_ID, EDITOR_MANAGER_PAGE) &&
            NATIVE_BUILDER_ID in pluginManager.bundledIds && registry.plugin(PLUGIN_EDITOR_ID) != null) {
            Destination.PluginPage(PLUGIN_EDITOR_ID, EDITOR_MANAGER_PAGE)
        } else requested
        if (destination is Destination.PluginPage && pluginManager.isPackagePaused(destination.pluginId)) return
        if (destination is Destination.PluginPage && sharing.showNative(destination.pluginId, destination.pageId)) return
        if (destination is Destination.CardEditor && cards.isReadOnly(destination.cardId)) {
            message(hostCopy("photo.received_read_only"))
            return
        }
        if (destination is Destination.CardPreview) {
            cardUi.open(StickerUiTarget.CARDS, destination.cardId)
            return
        }
        if (destination == Destination.Home) navigationHistory.clear()
        else if (state.value.destination != destination) navigationHistory.addLast(state.value.destination)
        update { it.copy(destination = destination, navigationStack = navigationHistory.toList(), editing = false) }
    }
    fun navigateBack() {
        (state.value.destination as? Destination.StickerReview)?.let { review -> viewModelScope.launch { stickerPlugins.reject(review.token) } }
        (state.value.destination as? Destination.QuickTextReview)?.let {quickTextPlugins.reject(it.token)}
        if (state.value.destination is Destination.ContentEditor && content.requestDiscard()) return
        if (state.value.destination is Destination.PhraseEditor) content.cancelPhrase()
        val previous = if (navigationHistory.isEmpty()) Destination.Home else navigationHistory.removeLast()
        update { it.copy(destination = previous, navigationStack = navigationHistory.toList(), editing = false) }
    }
    fun haptics(enabled: Boolean) {
        feedbackStore.save(enabled)
        update { it.copy(hapticsEnabled = enabled) }
    }
    fun mediaQuickTextCollapse(value: MediaQuickTextCollapse) {
        feedbackStore.saveMediaCollapse(value)
        update { it.copy(mediaQuickTextCollapse = value) }
    }
    fun appleLivePhotos(enabled: Boolean) {
        experiments.edit { putBoolean("apple-live-photos", enabled) }
        update { it.copy(appleLivePhotos = enabled) }
    }
    internal fun experimentalToolEnabled(tool: com.kigtts.next.manager.ExperimentalTool) =
        com.kigtts.next.manager.ExperimentalTool.enabled(getApplication<Application>(), tool.id)

    internal fun setExperimentalTool(tool: com.kigtts.next.manager.ExperimentalTool, enabled: Boolean) {
        if (tool.id in state.value.busyPlugins) return
        update { it.copy(busyPlugins = it.busyPlugins + tool.id) }
        com.kigtts.next.manager.ExperimentalTool.setEnabled(getApplication<Application>(), tool, enabled)
        viewModelScope.launch {
            try { refreshPlugins() }
            finally { update { it.copy(busyPlugins = it.busyPlugins - tool.id) } }
        }
    }
    fun mediaEditing(enabled: Boolean) { update { it.copy(mediaEditing = enabled) } }
    fun mediaScale(mode: com.kigtts.next.media.MediaScale) { state.value.mediaId?.let { id ->
        viewModelScope.launch { kotlinx.coroutines.withContext(Dispatchers.IO) { mediaRepository.scale(id, mode) }
            update { it.copy(subtitleRevision = it.subtitleRevision + 1) } }
    } }
    fun webHistory(canGoBack: Boolean) { update { it.copy(webCanGoBack = canGoBack) } }
    fun webBack() { update { it.copy(webBackRevision = it.webBackRevision + 1) } }
    fun openPanel(panel: HomePanel, dragHeight: Float? = null) {
        update { it.copy(destination = Destination.Home, panel = panel, panelHeight = dragHeight ?: it.lastExpandedPanelHeight, mediaSingleRow = false) }
    }
    fun openSidePage(page: SidePage) {
        if (page == SidePage.CARDS) { navigate(Destination.Cards); return }
        navigationHistory.clear()
        update { it.copy(destination = Destination.Home, navigationStack = emptyList(), sidePage = page, editing = false) }
    }
    fun quickImageCard(mediaId: String, title: String) {
        editPhraseDetails(null, state.value.selectedCategory)
        content.editPhrase { it.switchType(QuickCardType.MEDIA).attach(QuickCardType.MEDIA, mediaId).copy(text = title) }
    }
    fun addEditorToQuickText() {
        val current = state.value
        if (current.editorText.isBlank() || current.mediaId != null && mediaRepository.get(current.mediaId)?.kind != com.kigtts.next.media.MediaKind.AUDIO) return
        editPhraseDetails(null, current.selectedCategory)
        content.editPhrase { it.copy(text = current.editorText) }
    }
    fun closeSidePage() { update { it.copy(sidePage = null) } }
    fun closePanel() { update { it.copy(panel = null, quickTextEditing = false, mediaSingleRow = false) } }
    fun editQuickTexts() { update { it.copy(panel = HomePanel.QUICK_TEXT, quickTextEditing = true, editing = false, forcedFullscreen = false, mediaSingleRow = false) } }
    fun editPhraseDetails(phrase: QuickPhrase?, category: String) {
        content.beginPhrase(phrase, category)
        navigate(Destination.PhraseEditor(requireNotNull(content.phraseDraft.value).key, phrase == null))
    }
    fun finishQuickTextEditing() { update { it.copy(quickTextEditing = false) } }
    fun panelHeight(height: Float) {
        val value = height.coerceAtLeast(HomePanelSizes.COLLAPSED)
        if (value > HomePanelSizes.COLLAPSED) panelStore.saveHeight(value)
        update { it.copy(panelHeight = value, mediaSingleRow = false,
            lastExpandedPanelHeight = if (value > HomePanelSizes.COLLAPSED) value else it.lastExpandedPanelHeight) }
    }
    fun closeListeningPanel() = speech.closeListening()
    fun panelColumns(columns: Int) {
        val value = columns.coerceIn(1, 6)
        panelStore.saveColumns(value)
        update { it.copy(panelColumns = value, mediaSingleRow = false) }
    }
    fun captionHeight(height: Float) { update { it.copy(captionHeight = height) } }
    fun editing(editing: Boolean) {
        update { it.copy(editing = editing) }
    }
    fun submitSubtitle() {
        val current = state.value
        if (current.mediaId == null && current.audioId == null) publishBluetoothSubtitle(current.editorText)
        if(state.value.autoPlayback && state.value.mediaId == null) autoRead()
    }
    fun enterSubtitleFullscreen() { update { it.copy(forcedFullscreen = true) } }
    fun exitSubtitleFullscreen() { update { it.copy(editing = false, forcedFullscreen = false, mediaEditing = false) } }
    fun text(text: String) {
        content.resetSelection()
        if (audioSessionDelegate.isInitialized()) cardAudio.clear()
        update { it.copy(editorValue = TextFieldValue(text, TextRange(text.length)), mediaId = null,
            mediaEditing = false, webCanGoBack = false, audioId = null, subtitleFormats = emptyList(), subtitleRevision = it.subtitleRevision + 1,
            spokenPhraseId = null) }
    }
    fun playAudioCard(entry: com.kigtts.next.media.MediaEntry, autoPlay: Boolean = state.value.autoPlayback) {
        val file = mediaRepository.file(entry) ?: return message(hostCopy("audio.file_removed"))
        val legacy = quickTexts.phrases.value.firstOrNull { it.mediaId == entry.id }?.metadata.orEmpty()
        update { it.copy(audioId = entry.id) }; cardAudio.start(entry.id, android.net.Uri.fromFile(file), autoPlay,
            legacy["legacy.trimStartMs"]?.toLongOrNull() ?: 0L, legacy["legacy.trimEndMs"]?.toLongOrNull() ?: 0L)
    }
    fun toggleAutoPlayback() {
        setAutoPlayback(!state.value.autoPlayback)
    }
    fun setAutoPlayback(enabled: Boolean) {
        if (enabled && !synthesisGuidance.request()) return
        playbackPreferences.edit { putBoolean("automatic",enabled) }
        update { it.copy(autoPlayback=enabled) }
        if(!enabled) { if(audioSessionDelegate.isInitialized())cardAudio.pause();viewModelScope.launch { speech.stopSpeaking() } }
    }
    fun stopSpeechFromUi() { viewModelScope.launch { speech.stopSpeaking() } }
    private fun autoRead() {
        // A host preference; engines remain optional plugins and own all synthesis implementation.
        if(registry.plugins.any { it.tts!=null && registry.available(it) }&&state.value.mediaId==null&&state.value.editorText.isNotBlank())speech.speak(replace=true)
    }
    fun editText(value: TextFieldValue) { update { it.copy(editorValue = value,
        spokenPhraseId = it.spokenPhraseId.takeIf { _ -> value.text == it.editorText },
        subtitleFormats = com.kigtts.next.text.TextFormatting.rebase(it.subtitleFormats, it.editorText, value.text)) } }
    fun moveCursor(steps: Int) {
        if (steps == 0) return
        update { state ->
            val value = state.editorValue
            val breaks = BreakIterator.getCharacterInstance().apply { setText(value.text) }
            var cursor = if (steps < 0) value.selection.min else value.selection.max
            repeat(kotlin.math.abs(steps).coerceAtMost(128)) {
                val next = if (steps < 0) breaks.preceding(cursor) else breaks.following(cursor)
                cursor = if (next == BreakIterator.DONE) { if (steps < 0) 0 else value.text.length } else next
            }
            state.copy(editorValue = value.copy(selection = TextRange(cursor), composition = null))
        }
    }
    fun setSubtitleSizePercent(percent: Float) {
        val value = SubtitleSize.normalize(percent)
        subtitleSizeStore.save(value)
        update { it.copy(subtitleSizePercent = value) }
    }
    fun setCaptionTextSize(size: Float) { val saved = captionFonts.savePanel(size); update { it.copy(captionTextSize = saved) } }
    fun flipSubtitle() { update { it.copy(subtitleFlipped = !it.subtitleFlipped) } }
    fun setSubtitleAppearance(value: SubtitleAppearance) {
        subtitleAppearanceStore.save(value); update { it.copy(subtitleAppearance = value) }
    }
    fun formatSubtitle(range: TextRange, kind: com.kigtts.next.text.TextFormat, value: Long = 0) {
        if (!state.value.editing || !state.value.subtitleAppearance.richTextEditing) return
        update { it.copy(subtitleFormats = com.kigtts.next.text.TextFormatting.apply(it.subtitleFormats,
            it.editorText.length, range.start, range.end, kind, value)) }
    }
    fun category(category: String) { update { it.copy(selectedCategory = category) } }
    fun streaming(enabled: Boolean) {
        closeListeningPanel()
        update { it.copy(streaming = enabled, listening = false) }
    }
    fun theme(settings: ThemeSettings) {
        themeStore.save(settings)
        update { it.copy(theme = settings) }
    }
    internal fun answerSavedAppearance(request: SavedAppearanceRequest, accept: Boolean) {
        if (!savedAppearance.compareAndSet(request, null) || !accept) return
        if (registry.plugin(request.plugin.descriptor.id) === request.plugin) theme(request.appearance.applyTo(state.value.theme))
    }

    private suspend fun resolvePluginAppearance(pluginId: String, patch: AppearancePatch) =
        resolveAppearance(getApplication(), patch) { resource ->
            checkPluginAction(resource.pluginId == null || resource.pluginId == pluginId, "plugin.subtitle_resource_mismatch")
            pluginManager.paths.existingResource(pluginId, resource.path)
        }
    fun setSubtitlePlaceholder(value: String) {
        val normalized = SubtitlePlaceholder.normalize(value)
        placeholderStore.save(normalized); update { it.copy(subtitlePlaceholder = normalized) }
    }
    fun addPhrase(text: String, category: String) {
        quickTexts.add(text, category)
    }
    fun removePhrase(phrase: QuickPhrase) {
        quickTexts.remove(phrase.id)
    }
    fun invoke(action: RegisteredAction) = invoke(action.plugin.descriptor.id, action.action.id)
    private val controlChanges by lazy { PluginControlQueue(viewModelScope) { plugin, action, inputs ->
        state.first { plugin !in it.busyPlugins }
        val completed = kotlinx.coroutines.CompletableDeferred<Unit>()
        invokeWithResult(plugin, action, inputs) { completed.complete(Unit) }
        completed.await()
    } }
    fun invokeControl(pluginId: String, actionId: String, value: String? = null) {
        controlChanges.submit(pluginId, actionId, value?.let { mapOf("value" to it) } ?: emptyMap())
    }
    fun invoke(pluginId: String, actionId: String, inputs: Map<String, String> = emptyMap()) {
        invokeWithResult(pluginId, actionId, inputs, null)
    }
    private fun invokeWithResult(pluginId: String, actionId: String, inputs: Map<String, String>, complete: ((Boolean) -> Unit)?) {
        try { agentAccess.userAction(pluginId, inputs) }
        catch (error: IllegalArgumentException) { message(hostCopy("plugin.permission_unconfirmed")); complete?.invoke(false); return }
        val plugin = registry.plugin(pluginId)
        if (plugin == null) { complete?.invoke(false); return }
        if (pluginId == "service.speech-synthesis" && actionId == "import-voice-package") {
            externalFiles.chooseVoice()
            complete?.invoke(true)
            return
        }
        if (runCatching { sharing.handle(pluginId, actionId, inputs) }.getOrElse { message(hostCopy("plugin.share_failed")); true }) {
            complete?.invoke(true); return
        }
        if (runCatching { audioDevices.handle(plugin, actionId, inputs) }.getOrElse { message(hostCopy("plugin.audio_settings_failed")); true }) {
            update { it.copy(pluginRevision = it.pluginRevision + 1) }; complete?.invoke(true); return
        }
        if (pluginId in state.value.busyPlugins) { complete?.invoke(false); return }
        val invokingPage = state.value.pageVersion
        update { it.copy(busyPlugins = it.busyPlugins + pluginId) }
        viewModelScope.launch {
            var accepted = false
            try {
                val executionContext = context().let { it.copy(inputs = inputs + it.inputs) }
                val outcome = executeWithReadingCache(hostRuntime?.reading, plugin, actionId, executionContext)
                if (handleServiceOutcome(outcome, plugin, pluginManager, context(), { speech.closeListening(); speech.stopAll() },
                        ::refreshPlugins, { selectEngine(it, false) }, ::navigate)) return@launch
                when (outcome) {
                    is PluginOutcome.StartRecognition -> {
                        checkPluginAction(plugin.asr?.info?.role == AsrRole.SERVICE && outcome.session.serviceId == pluginId,
                            "plugin.recognition_session_not_allowed")
                        val session = outcome.session
                        captionOutput.start(if (pluginId == "service.speech-recognition" && session.purpose == RecognitionPurpose.CAPTIONS)
                            session.copy(output = CaptionOutput.PANEL) else session)
                        accepted = session.purpose == RecognitionPurpose.VOICE_INPUT
                    }
                    is PluginOutcome.EndRecognition -> {
                        checkPluginAction(plugin.asr?.info?.role == AsrRole.SERVICE, "plugin.recognition_session_not_allowed")
                        speech.end(outcome.submit)
                    }
                    is PluginOutcome.SelectTtsService -> selectEngine(outcome.pluginId, true)
                    PluginOutcome.RefreshUi -> update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    is PluginOutcome.HostAction -> { checkPluginAction(registry.available(plugin), "plugin.subtitle_requires_recognition"); when (outcome.command) {
                        HostCommand.TOGGLE_LISTENING -> toggleListening()
                        HostCommand.CLOSE_LISTENING_PANEL -> closeListeningPanel()
                        HostCommand.OPEN_LISTENING_PANEL -> update { it.copy(captionsVisible = true) }
                        HostCommand.USE_STREAMING -> streaming(true)
                        HostCommand.USE_BATCH -> streaming(false)
                        HostCommand.VOICE_INPUT -> { speech.voiceInput(); accepted = true }
                        HostCommand.TOGGLE_SUBTITLE_FULLSCREEN -> if (state.value.subtitleExpanded || state.value.forcedFullscreen)
                            exitSubtitleFullscreen() else enterSubtitleFullscreen()
                    } }
                    PluginOutcome.NoAction -> Unit
                    is PluginOutcome.SendQuickText -> {
                        checkPluginAction(pluginManager.requestPermission(pluginId, "quick-text-read"), "plugin.quick_text_permission_required")
                        quickTexts.phrases.value.firstOrNull { it.id == outcome.entryId }?.let(content::send)
                    }
                    is PluginOutcome.OpenPage -> {
                        if (plugin.page(outcome.pageId, context()) != null) {
                            val target = Destination.PluginPage(pluginId, outcome.pageId)
                            if (state.value.destination == target) update { it.copy(pluginRevision = it.pluginRevision + 1) }
                            else navigate(target)
                            accepted = true
                        } else message(hostCopy("plugin.page_missing"))
                    }
                    is PluginOutcome.ReplaceEditorText -> { text(outcome.text); navigate(Destination.Home) }
                    is PluginOutcome.SetSubtitleRenderer -> {
                        val resource = outcome.root
                        if (resource == null) subtitleRenderer.clear(pluginId)
                        else {
                            checkPluginAction(resource.pluginId == null || resource.pluginId == pluginId, "plugin.subtitle_resource_mismatch")
                            subtitleRenderer.select(pluginId, plugin, pluginManager.paths.storage(pluginId).file(resource.path), outcome.mainPage, outcome.presentation)
                        }
                        pluginManager.refreshUi(pluginId, context())
                        update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    }
                    is PluginOutcome.SetNativeSubtitleRenderer -> {
                        val renderer = outcome.renderer
                        val appearance = outcome.appearance?.let { resolvePluginAppearance(pluginId, it) }
                        if (registry.plugin(pluginId) !== plugin || state.value.pageVersion != invokingPage) return@launch
                        if (renderer == null) subtitleRenderer.clear(pluginId)
                        else subtitleRenderer.selectNative(plugin, renderer, outcome.presentation, appearance,
                            outcome.collapseQuickText, ::closePanel)
                        outcome.controls?.let { outcomes.send(pluginId to it) }
                        pluginManager.refreshUi(pluginId, context())
                        update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    }
                    is PluginOutcome.SetTemporaryAppearance -> {
                        val appearance = outcome.appearance?.let { resolvePluginAppearance(pluginId, it) }
                        if (registry.plugin(pluginId) !== plugin || state.value.pageVersion != invokingPage) return@launch
                        pageAppearance.update { current ->
                            val remaining = current.filterNot { it.plugin === plugin }
                            if (appearance == null) remaining else remaining + PageAppearance(plugin, invokingPage, appearance)
                        }
                    }
                    is PluginOutcome.ChangeSavedAppearance -> {
                        val appearance = resolvePluginAppearance(pluginId, outcome.appearance)
                        if (registry.plugin(pluginId) === plugin && state.value.pageVersion == invokingPage)
                            savedAppearance.compareAndSet(null, SavedAppearanceRequest(plugin, appearance))
                    }
                    is PluginOutcome.SetHomeChrome -> {
                        if (registry.plugin(pluginId) !== plugin) return@launch
                        val presentation = outcome.presentation
                        homeChrome.update { if (presentation == null) it - plugin else it + (plugin to presentation) }
                    }
                    is PluginOutcome.SetListeningPanel -> {
                        listeningPanel.select(plugin, outcome, context())
                        pluginManager.refreshUi(pluginId, context())
                        update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    }
                    is PluginOutcome.PresentContent -> {
                        val network = when (val media = outcome.content) { is SubtitleContent.Web -> true; is SubtitleContent.Html -> media.allowNetwork; else -> false }
                        checkPluginAction(!network || pluginManager.status.any {
                            it.installed.manifest.id == pluginId && "network" in it.installed.manifest.capabilities
                        }, "plugin.web_network_required")
                        content.present(pluginId, outcome.content); navigate(Destination.Home)
                    }
                    else -> outcomes.send(pluginId to outcome)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { showPluginFailure(pluginId = pluginId, error = error) }
            finally {
                update { it.copy(busyPlugins = it.busyPlugins - pluginId) }
                complete?.invoke(accepted)
                sourceLinks.deliver()
                sharing.deliver()
            }
        }
    }
    private fun showPluginFailure(error: Exception, pluginId: String? = null) {
        android.util.Log.w("PluginAction", "Plugin action failed for ${pluginId.orEmpty()}", error)
        message(hostCopy((error as? PluginActionDenied)?.copyKey ?: "plugin.action_failed"))
    }
    private fun checkPluginAction(allowed: Boolean, key: String) {
        if (!allowed) throw PluginActionDenied(key)
    }
    private class PluginActionDenied(val copyKey: String) : IllegalStateException(copyKey)
    fun message(text: String) { viewModelScope.launch { outcomes.send("host" to PluginOutcome.Message(text)) } }
    init { com.kigtts.next.cards.observePolaroidSystemAlbum(viewModelScope, cards, ::message) }
    internal fun rendererUnavailable(owner: String) {
        subtitleRenderer.clear(owner)
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
    }

    fun toggleListening() = speech.toggleListening()
    fun speak() {
        if (state.value.mediaId != null) message(hostCopy("reading.text_only"))
        else if (state.value.speaking || state.value.editorText.isBlank() || synthesisGuidance.request()) speech.speak()
    }
    suspend fun refreshPlugins() {
        safeModeSession.synchronize()
        pluginManager.reload()
        subtitleRenderer.reconcile(registry)
        homeChrome.update { requests -> requests.filterKeys { registry.plugin(it.descriptor.id) === it } }
        pageAppearance.update { patches -> patches.filter { registry.plugin(it.plugin.descriptor.id) === it.plugin } }
        savedAppearance.value?.takeUnless { registry.plugin(it.plugin.descriptor.id) === it.plugin }
            ?.let { savedAppearance.compareAndSet(it, null) }
        listeningPanel.reconcile(registry)
        pluginManager.refreshContext(context())
        for (key in listOf("tts", "asr")) {
            val current = engineSelection.getString(key, null)
            val next = chooseEngine(registry.plugins, current, key == "tts")
            if (next != current) engineSelection.edit { putString(key, next) }
        }
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
        synthesisGuidance.refresh()
        if (!captionServiceReady) { speech.closeListening(); captionOutput.resetWindow() }
        pluginCatalogReady = true
        sharing.deliver()
        externalFiles.pluginsChanged()
        legacyPiperGuidance.onStartup()
        sourceLinks.deliver()
    }
    fun selectEngine(id: String, tts: Boolean) {
        val plugin = registry.plugin(id) ?: return
        require(if (tts) plugin.tts != null else plugin.asr != null)
        if (!tts && plugin.asr?.info?.role != AsrRole.SERVICE) {
            message(hostCopy("listening.engine_setup")); return
        }
        if (tts && plugin.tts?.info?.role != TtsRole.SERVICE && registry.plugins.any { it.tts?.info?.role == TtsRole.SERVICE }) {
            message(hostCopy("reading.engine_missing")); return
        }
        viewModelScope.launch {
            speech.stopAll()
            engineSelection.edit { putString(if (tts) "tts" else "asr", id) }
            update { it.copy(pluginRevision = it.pluginRevision + 1) }
        }
    }
    fun changePlugin(id: String, enabled: Boolean? = null) {
        if (id in state.value.busyPlugins) return
        update { it.copy(busyPlugins = it.busyPlugins + id) }
        viewModelScope.launch {
            try {
                speech.stopAll()
                if (enabled == null) pluginManager.uninstall(id) else pluginManager.enable(id, enabled)
                refreshPlugins()
            } catch (error: Exception) { showPluginFailure(error = error) }
            finally { update { it.copy(busyPlugins = it.busyPlugins - id) } }
        }
    }
    suspend fun stopSpeech() = speech.stopAll()
    fun onBackground() = speech.onBackground(captionOutput.windowOpen())
    override fun onCleared() {
        super.onCleared()
        sharing.close()
        if (audioSessionDelegate.isInitialized()) cardAudio.release()
        if (bluetoothSubtitles.isInitialized()) bluetoothSubtitles.value.setEnabled(false)
        audioDevices.close()
        captionOutput.close()
        hostRuntime?.detach(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            speech.stopAll()
            if (hostRuntime == null) withContext(Dispatchers.IO) { pluginManager.close() }
        }
    }
}
