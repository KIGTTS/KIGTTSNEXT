package com.kigtts.next.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.ThemeSettingsStore
import com.kigtts.plugin.api.*
import kotlinx.coroutines.CancellationException
import com.kigtts.next.audio.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import android.icu.text.BreakIterator
import androidx.core.content.edit
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.i18n.uiCopy

class AppViewModel @JvmOverloads constructor(application: Application, engineFixtures: List<KigPlugin> = emptyList(),
    audio: AudioPlatform = AndroidAudioPlatform(application)) : AndroidViewModel(application) {
    init { com.kigtts.next.ui.components.MaterialSymbols.initialize(application) }
    val pluginChatHistory = com.kigtts.next.ui.plugins.ChatHistoryCache()
    val sketch = com.kigtts.next.ui.sketch.SketchState()
    private val hostRuntime = if (engineFixtures.isEmpty()) HostPluginRuntime.get(application) else null
    val pluginManager = hostRuntime?.manager ?: PluginManager(application, engineFixtures, com.kigtts.next.sharing.pausedSharingPlugins)
    private val recovery = hostRuntime?.recovery ?: com.kigtts.next.recovery.CrashRecovery(application)
    internal val safeModeSession = com.kigtts.next.recovery.SafeModeSession(recovery, ::refreshPlugins)
    init { pluginManager.safeMode = recovery::safeMode }
    val registry = pluginManager.registry
    val readingCacheBytes get() = hostRuntime?.reading?.cacheBytes
    val audioDevices = (audio as? AndroidAudioPlatform)?.devices ?: AudioDeviceRouting(application)
    fun pluginPage(pluginId: String, pageId: String): PluginPage? = registry.plugin(pluginId)?.let { plugin ->
        audioDevices.decorate(plugin, plugin.page(pageId, context()))
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
    private val initialUiLocale = uiLanguage.load()
    private val mutableState = MutableStateFlow(AppState(uiLocale = initialUiLocale, theme = themeStore.load(), subtitlePlaceholder = subtitlePlaceholder(initialUiLocale), hapticsEnabled = feedbackStore.load(),
        subtitleAppearance = subtitleAppearanceStore.load(),
        lastExpandedPanelHeight = panelStore.loadHeight(), panelColumns = panelStore.loadColumns(), subtitleSizePercent = subtitleSizeStore.load(),
        appleLivePhotos = experiments.getBoolean("apple-live-photos", false),
        autoPlayback = playbackPreferences.getBoolean("automatic", false),
        contentAutoPlay = presentationPreferences.getBoolean("autoPlay", true),
        contentAudioMode = com.kigtts.next.media.MediaAudioMode.read(presentationPreferences.getString("audioMode", null))))
    val state = mutableState.asStateFlow()
    init { audioDevices.onChanged = { update { it.copy(pluginRevision = it.pluginRevision + 1) } } }
    val cameraSaves = CameraSaveQueue(cards, stickers, viewModelScope, ::message, saveMotion = { imageId, clip ->
        cardMotions.import(imageId, clip.video, clip.durationMs, clip.rotationDegrees, clip.shutterPositionMs, clip.colorMatrix, clip.lut, clip.lutStrength, clip.effects)
    })
    fun saveCameraVideo(file: java.io.File, timeLapse: Boolean, onSaved: (android.net.Uri) -> Unit = {}) { viewModelScope.launch {
        try { onSaved(com.kigtts.next.media.saveCameraVideo(getApplication(), file, timeLapse)) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message("视频未能保存，原文件已保留") }
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
    internal val sharing by lazy { com.kigtts.next.sharing.SharingCoordinator(this, viewModelScope) }
    internal val legacyImports = com.kigtts.next.legacy.LegacyImportController(application, quickTexts, mediaRepository, cards, viewModelScope, ::message)
    val quickTextPlugins=hostRuntime?.quickTextPlugins ?: com.kigtts.next.plugins.quicktext.QuickTextHost(quickTexts,mediaRepository,pluginManager.paths) {this}
    init {if(hostRuntime==null)pluginManager.quickTextBrokerFactory=quickTextPlugins::forPlugin}
    private val appPlugins = hostRuntime?.appPlugins ?: com.kigtts.next.plugins.control.AppControlHost(pluginManager) { this }
    init { if (hostRuntime == null) pluginManager.appBrokerFactory = appPlugins::forPlugin }
    init { if (state.value.selectedCategory !in categories.value) category(categories.value.firstOrNull() ?: "常用") }
    private val outcomes = Channel<Pair<String, PluginOutcome>>(Channel.BUFFERED)
    val events = outcomes.receiveAsFlow()
    init { legacyImports.migrateOriginalInstallation() }
    private val speech = SpeechCoordinator(viewModelScope, registry, audio, { state.value }, ::update, ::message, ::text,
        selection = { if (it) selectedTtsId else selectedAsrId }, automaticRead = ::setAutoPlayback)
    private val captionOutput = CaptionOutputCoordinator(application, viewModelScope, state, speech::start, speech::closeListening, ::message) {
        selectedAsrId ?: registry.plugins.firstOrNull { it.asr?.info?.role == AsrRole.SERVICE }?.descriptor?.id
    }
    val overlayRequests = captionOutput.permissionRequests
    fun overlayResult() = captionOutput.permissionResult()
    fun openCaptionWindow() = captionOutput.openWindow()
    fun resumeCaptionWindow() = captionOutput.resume()
    val voiceInputGesture = VoiceInputGestureState()
    fun endVoiceInput(submit: Boolean = false, read: Boolean = false) = speech.end(submit, read)
    fun continueVoiceInput() = speech.continueInput()
    val microphoneRequests = speech.permissionRequests
    fun microphoneResult(granted: Boolean) = speech.permissionResult(granted)
    // Every callback dependency must exist before bootstrap can resume from background I/O.
    private val navigationHistory = ArrayDeque<Destination>()
    internal var pluginCatalogReady = engineFixtures.isNotEmpty()
    internal val storeBootstrap: com.kigtts.next.plugins.control.StoreBootstrapCoordinator by lazy { com.kigtts.next.plugins.control.StoreBootstrapCoordinator(viewModelScope,
        installed = { pluginManager.status.any { it.installed.manifest.id == com.kigtts.next.plugins.control.STORE_ID } },
        ready = { registry.plugins.any { registry.available(it) && it.contributions.sourceAction != null } },
        download = { com.kigtts.next.plugins.control.StoreBootstrapDownload(pluginManager.paths.packages, pluginManager.packages::inspect).download() },
        install = { candidate -> stopSpeech(); pluginManager.install(candidate.file, candidate.preview.publisherSha256); refreshPlugins() },
        restore = { pluginManager.enable(com.kigtts.next.plugins.control.STORE_ID, true); refreshPlugins() },
        complete = { sourceLinks.deliver() }) }
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
    internal fun openSpeechPluginStore(query: String) {
        storeBootstrap.offer(com.kigtts.next.plugins.control.StoreBootstrapPurpose.SYNTHESIS) {
            invokeWithResult(com.kigtts.next.plugins.control.STORE_ID, "search-save", mapOf("value" to query)) { accepted ->
                if (accepted) navigate(Destination.PluginPage(com.kigtts.next.plugins.control.STORE_ID, "search"))
            }
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
        ready = { pluginCatalogReady }, missingStore = { storeBootstrap.offer() }) }
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
    init { hostRuntime?.let { host -> host.attach(this); viewModelScope.launch { host.revision.drop(1).collect { refreshPlugins() } } } }
    fun sourceLink(uri: android.net.Uri?) = sourceLinks.accept(uri)

    fun externalIntent(intent: android.content.Intent?) { if (sharing.accept(intent)) return; externalFiles.accept(intent); externalLinks.accept(intent) }

    private fun update(transform: (AppState) -> AppState) {
        mutableState.update(transform)
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
        if (state.value.uiLocale == language.tag) return
        uiLanguage.save(language)
        update { it.copy(uiLocale = language.tag, subtitlePlaceholder = subtitlePlaceholder(language.tag)) }
    }
    private fun subtitlePlaceholder(locale: String) =
        placeholderStore.load(getApplication<Application>().uiCopy("subtitle.placeholder", locale = locale))
    fun navigate(destination: Destination) {
        if (destination is Destination.PluginPage && pluginManager.isPackagePaused(destination.pluginId)) return
        if (destination is Destination.PluginPage && sharing.showNative(destination.pluginId, destination.pageId)) return
        if (destination is Destination.CardEditor && cards.isReadOnly(destination.cardId)) {
            message("接收的照片只能查看和分享")
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
    fun appleLivePhotos(enabled: Boolean) {
        experiments.edit { putBoolean("apple-live-photos", enabled) }
        update { it.copy(appleLivePhotos = enabled) }
    }
    fun mediaEditing(enabled: Boolean) { update { it.copy(mediaEditing = enabled) } }
    fun mediaScale(mode: com.kigtts.next.media.MediaScale) { state.value.mediaId?.let { id ->
        viewModelScope.launch { kotlinx.coroutines.withContext(Dispatchers.IO) { mediaRepository.scale(id, mode) }
            update { it.copy(subtitleRevision = it.subtitleRevision + 1) } }
    } }
    fun webHistory(canGoBack: Boolean) { update { it.copy(webCanGoBack = canGoBack) } }
    fun webBack() { update { it.copy(webBackRevision = it.webBackRevision + 1) } }
    fun openPanel(panel: HomePanel, dragHeight: Float? = null) {
        update { it.copy(destination = Destination.Home, panel = panel, panelHeight = dragHeight ?: it.lastExpandedPanelHeight) }
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
    fun closePanel() { update { it.copy(panel = null, quickTextEditing = false) } }
    fun editQuickTexts() { update { it.copy(panel = HomePanel.QUICK_TEXT, quickTextEditing = true, editing = false, forcedFullscreen = false) } }
    fun editPhraseDetails(phrase: QuickPhrase?, category: String) {
        content.beginPhrase(phrase, category)
        navigate(Destination.PhraseEditor(requireNotNull(content.phraseDraft.value).key, phrase == null))
    }
    fun finishQuickTextEditing() { update { it.copy(quickTextEditing = false) } }
    fun panelHeight(height: Float) {
        val value = height.coerceAtLeast(HomePanelSizes.COLLAPSED)
        if (value > HomePanelSizes.COLLAPSED) panelStore.saveHeight(value)
        update { it.copy(panelHeight = value,
            lastExpandedPanelHeight = if (value > HomePanelSizes.COLLAPSED) value else it.lastExpandedPanelHeight) }
    }
    fun closeListeningPanel() = speech.closeListening()
    fun panelColumns(columns: Int) {
        val value = columns.coerceIn(1, 6)
        panelStore.saveColumns(value)
        update { it.copy(panelColumns = value) }
    }
    fun captionHeight(height: Float) { update { it.copy(captionHeight = height) } }
    fun editing(editing: Boolean) {
        update { it.copy(editing = editing) }
    }
    fun submitSubtitle() {
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
        val file = mediaRepository.file(entry) ?: return message("音频文件已不可用")
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
    fun setCaptionTextSize(size: Float) { update { it.copy(captionTextSize = size.coerceIn(10f, 64f)) } }
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
        catch (error: IllegalArgumentException) { message(error.message ?: "权限模式未确认"); complete?.invoke(false); return }
        val plugin = registry.plugin(pluginId)
        if (plugin == null) { complete?.invoke(false); return }
        if (pluginId == "service.speech-synthesis" && actionId == "import-voice-package") {
            externalFiles.chooseVoice()
            complete?.invoke(true)
            return
        }
        if (runCatching { sharing.handle(pluginId, actionId, inputs) }.getOrElse { message(it.message ?: "分享暂不可用"); true }) {
            complete?.invoke(true); return
        }
        if (runCatching { audioDevices.handle(plugin, actionId, inputs) }.getOrElse { message(it.message ?: "音频设置失败"); true }) {
            update { it.copy(pluginRevision = it.pluginRevision + 1) }; complete?.invoke(true); return
        }
        if (pluginId in state.value.busyPlugins) { complete?.invoke(false); return }
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
                        check(plugin.asr?.info?.role == AsrRole.SERVICE && outcome.session.serviceId == pluginId) { "只有识别服务可以创建识别会话" }
                        captionOutput.start(outcome.session)
                    }
                    is PluginOutcome.EndRecognition -> { check(plugin.asr?.info?.role == AsrRole.SERVICE); speech.end(outcome.submit) }
                    is PluginOutcome.SelectTtsService -> selectEngine(outcome.pluginId, true)
                    PluginOutcome.RefreshUi -> update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    is PluginOutcome.HostAction -> { check(registry.available(plugin)) { "此插件需要先启用语音识别服务" }; when (outcome.command) {
                        HostCommand.TOGGLE_LISTENING -> toggleListening()
                        HostCommand.CLOSE_LISTENING_PANEL -> closeListeningPanel()
                        HostCommand.OPEN_LISTENING_PANEL -> update { it.copy(captionsVisible = true) }
                        HostCommand.USE_STREAMING -> streaming(true)
                        HostCommand.USE_BATCH -> streaming(false)
                        HostCommand.VOICE_INPUT -> speech.voiceInput()
                        HostCommand.TOGGLE_SUBTITLE_FULLSCREEN -> if (state.value.subtitleExpanded || state.value.forcedFullscreen)
                            exitSubtitleFullscreen() else enterSubtitleFullscreen()
                    } }
                    PluginOutcome.NoAction -> Unit
                    is PluginOutcome.SendQuickText -> {
                        check(pluginManager.requestPermission(pluginId, "quick-text-read")) { "需要允许读取快捷文本" }
                        quickTexts.phrases.value.firstOrNull { it.id == outcome.entryId }?.let(content::send)
                    }
                    is PluginOutcome.OpenPage -> {
                        if (plugin.page(outcome.pageId, context()) != null) { navigate(Destination.PluginPage(pluginId, outcome.pageId)); accepted = true }
                        else message("插件页面不存在")
                    }
                    is PluginOutcome.ReplaceEditorText -> { text(outcome.text); navigate(Destination.Home) }
                    is PluginOutcome.SetSubtitleRenderer -> {
                        val resource = outcome.root
                        if (resource == null) subtitleRenderer.clear(pluginId)
                        else {
                            require(resource.pluginId == null || resource.pluginId == pluginId) { "字幕资源不属于此插件" }
                            subtitleRenderer.select(pluginId, plugin, pluginManager.paths.storage(pluginId).file(resource.path), outcome.mainPage, outcome.presentation)
                        }
                        pluginManager.refreshUi(pluginId, context())
                        update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    }
                    is PluginOutcome.SetListeningPanel -> {
                        listeningPanel.select(plugin, outcome, context())
                        pluginManager.refreshUi(pluginId, context())
                        update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    }
                    is PluginOutcome.PresentContent -> {
                        val network = when (val media = outcome.content) { is SubtitleContent.Web -> true; is SubtitleContent.Html -> media.allowNetwork; else -> false }
                        check(!network || plugin.descriptor.id in pluginManager.status.filter { "network" in it.installed.manifest.capabilities }.map { it.installed.manifest.id }) { "插件需要声明 network 能力才能加载网络网页" }
                        content.present(pluginId, outcome.content); navigate(Destination.Home)
                    }
                    else -> outcomes.send(pluginId to outcome)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "插件操作失败，请重试") }
            finally {
                update { it.copy(busyPlugins = it.busyPlugins - pluginId) }
                complete?.invoke(accepted)
                sourceLinks.deliver()
                sharing.deliver()
            }
        }
    }
    fun message(text: String) { viewModelScope.launch { outcomes.send("host" to PluginOutcome.Message(text)) } }
    init { com.kigtts.next.cards.observePolaroidSystemAlbum(viewModelScope, cards, ::message) }
    internal fun rendererUnavailable(owner: String) {
        subtitleRenderer.clear(owner)
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
    }

    fun toggleListening() = speech.toggleListening()
    fun speak() {
        if (state.value.mediaId != null) message("请切换到文本后朗读")
        else if (state.value.speaking || synthesisGuidance.request()) speech.speak()
    }
    suspend fun refreshPlugins() {
        safeModeSession.synchronize()
        pluginManager.reload()
        subtitleRenderer.reconcile(registry)
        listeningPanel.reconcile(registry)
        pluginManager.refreshContext(context())
        for (key in listOf("tts", "asr")) {
            val current = engineSelection.getString(key, null)
            val next = chooseEngine(registry.plugins, current, key == "tts")
            if (next != current) engineSelection.edit { putString(key, next) }
        }
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
        synthesisGuidance.refresh()
        if (registry.plugins.none { it.asr?.info?.role == AsrRole.SERVICE }) speech.closeListening()
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
            message("请在语音识别服务中选用识别引擎"); return
        }
        if (tts && plugin.tts?.info?.role != TtsRole.SERVICE && registry.plugins.any { it.tts?.info?.role == TtsRole.SERVICE }) {
            message("请在朗读设置中选择引擎和声音"); return
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
            } catch (error: Exception) { message(error.message ?: "插件操作失败") }
            finally { update { it.copy(busyPlugins = it.busyPlugins - id) } }
        }
    }
    suspend fun stopSpeech() = speech.stopAll()
    fun onBackground() { if (!state.value.listening || state.value.voiceInputActive || state.value.recognitionOutput == CaptionOutput.PANEL) viewModelScope.launch { speech.stopAll() } }
    override fun onCleared() {
        super.onCleared()
        storeBootstrap.close()
        sharing.close()
        if (audioSessionDelegate.isInitialized()) cardAudio.release()
        audioDevices.close()
        captionOutput.close()
        hostRuntime?.detach(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { speech.stopAll(); if (hostRuntime == null) pluginManager.close() }
    }
}
