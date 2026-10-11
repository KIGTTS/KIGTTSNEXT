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

class AppViewModel @JvmOverloads constructor(application: Application, engineFixtures: List<KigPlugin> = emptyList(),
    audio: AudioPlatform = AndroidAudioPlatform(application)) : AndroidViewModel(application) {
    private val hostRuntime = if (engineFixtures.isEmpty()) HostPluginRuntime.get(application) else null
    val pluginManager = hostRuntime?.manager ?: PluginManager(application, engineFixtures + builtinPlugins(application))
    val registry = pluginManager.registry
    private val engineSelection = application.getSharedPreferences("engine-selection", android.content.Context.MODE_PRIVATE)
    val selectedTtsId get() = engineSelection.getString("tts", null)
    val selectedAsrId get() = engineSelection.getString("asr", null)
    private val managementOrder = ManagementOrderStore(application)
    fun pluginOrder(ids: List<String>) = managementOrder.ordered("plugins", ids)
    fun reorderPlugins(ids: List<String>) {
        val current = (pluginManager.status.map { it.installed.manifest.id } + registry.plugins.map { it.descriptor.id }).distinct()
        managementOrder.save("plugins", ids, current)
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
    }
    private val themeStore = ThemeSettingsStore(application)
    private val feedbackStore = FeedbackSettingsStore(application)
    private val phraseStore = QuickPhraseStore(application)
    private val panelStore = HomePanelStore(application)
    private val subtitleSizeStore = SubtitleSizeStore(application)
    private val experiments = application.getSharedPreferences("experiments", 0)
    private val playbackPreferences = application.getSharedPreferences("playback", 0)
    private val presentationPreferences = application.getSharedPreferences("media-presentation", 0)
    val mediaRepository = MediaRepository(application, pluginManager.paths)
    private val audioSessionDelegate = lazy { AudioCardSession(application) }
    val cardAudio get() = audioSessionDelegate.value
    private val mutableState = MutableStateFlow(AppState(theme = themeStore.load(), hapticsEnabled = feedbackStore.load(),
        lastExpandedPanelHeight = panelStore.loadHeight(), panelColumns = panelStore.loadColumns(), subtitleSizePercent = subtitleSizeStore.load(),
        appleLivePhotos = experiments.getBoolean("apple-live-photos", false),
        autoPlayback = playbackPreferences.getBoolean("automatic", true),
        contentAutoPlay = presentationPreferences.getBoolean("autoPlay", true),
        contentAudioMode = com.kigtts.next.media.MediaAudioMode.read(presentationPreferences.getString("audioMode", null)),
        mediaId = mediaRepository.get(presentationPreferences.getString("selected", null))?.id))
    val state = mutableState.asStateFlow()
    val quickTexts = QuickTextLibrary(phraseStore)
    val content = ContentWorkspace(application, mediaRepository, quickTexts, { state.value }, ::update, ::navigate, ::navigateBack, ::text,
        ::playAudioCard, { if(audioSessionDelegate.isInitialized())cardAudio.clear();viewModelScope.launch { speech.stopSpeaking() } }, ::autoRead)
    val phrases = quickTexts.phrases
    val categories = quickTexts.categories
    init { if (state.value.selectedCategory !in categories.value) category(categories.value.firstOrNull() ?: "常用") }
    private val outcomes = Channel<Pair<String, PluginOutcome>>(Channel.BUFFERED)
    val events = outcomes.receiveAsFlow()
    private val speech = SpeechCoordinator(viewModelScope, registry, audio, { state.value }, ::update, ::message, ::text) {
        if (it) selectedTtsId else selectedAsrId
    }
    val microphoneRequests = speech.permissionRequests
    fun microphoneResult(granted: Boolean) = speech.permissionResult(granted)
    init { if (engineFixtures.isEmpty()) viewModelScope.launch { refreshPlugins() } }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observePluginContext() = viewModelScope.launch {
        state.map { context() }
            .distinctUntilChanged().debounce(100).collect { context ->
                if (pluginManager.refreshContext(context)) update { it.copy(pluginRevision = it.pluginRevision + 1) }
            }
    }
    init { if (engineFixtures.isEmpty()) observePluginContext() }
    init { hostRuntime?.let { host -> host.attach(this); viewModelScope.launch { host.revision.drop(1).collect { refreshPlugins() } } } }
    private val navigationHistory = ArrayDeque<Destination>()

    private fun update(transform: (AppState) -> AppState) { mutableState.update(transform) }
    fun context() = state.value.let { PluginContext(it.editorText, it.transcript, it.listening, it.captionsVisible,
        contentKind = mediaRepository.get(it.mediaId)?.kind?.name ?: "TEXT", contentId = it.mediaId) }
    fun navigate(destination: Destination) {
        if (destination == Destination.Home) navigationHistory.clear()
        else if (state.value.destination != destination) navigationHistory.addLast(state.value.destination)
        update { it.copy(destination = destination, navigationStack = navigationHistory.toList(), editing = false) }
    }
    fun navigateBack() {
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
    fun openSidePage(page: SidePage) { update { it.copy(destination = Destination.Home, sidePage = page, editing = false) } }
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
        val finished=state.value.editing&&!editing
        update { it.copy(editing = editing) }
        if(finished&&state.value.autoPlayback&&state.value.mediaId==null)autoRead()
    }
    fun enterSubtitleFullscreen() { update { it.copy(forcedFullscreen = true) } }
    fun exitSubtitleFullscreen() { update { it.copy(editing = false, forcedFullscreen = false, mediaEditing = false) } }
    fun text(text: String) {
        content.resetSelection()
        if (audioSessionDelegate.isInitialized()) cardAudio.clear()
        update { it.copy(editorValue = TextFieldValue(text, TextRange(text.length)), mediaId = null,
            mediaEditing = false, webCanGoBack = false, audioId = null, subtitleFormats = emptyList(), subtitleRevision = it.subtitleRevision + 1) }
    }
    fun playAudioCard(entry: com.kigtts.next.media.MediaEntry, autoPlay: Boolean = state.value.autoPlayback) {
        val file = mediaRepository.file(entry) ?: return message("音频文件已不可用")
        update { it.copy(audioId = entry.id) }; cardAudio.start(entry.id, android.net.Uri.fromFile(file),autoPlay)
    }
    fun toggleAutoPlayback() {
        val enabled=!state.value.autoPlayback
        playbackPreferences.edit { putBoolean("automatic",enabled) }
        update { it.copy(autoPlayback=enabled) }
        if(!enabled) { if(audioSessionDelegate.isInitialized())cardAudio.pause();viewModelScope.launch { speech.stopSpeaking() } }
    }
    private fun autoRead() {
        // A host preference; engines remain optional plugins and own all synthesis implementation.
        if(registry.plugins.any { it.tts!=null && registry.available(it) }&&state.value.mediaId==null&&state.value.editorText.isNotBlank())speech.speak(replace=true)
    }
    fun editText(value: TextFieldValue) { update { it.copy(editorValue = value,
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
    fun category(category: String) { update { it.copy(selectedCategory = category) } }
    fun streaming(enabled: Boolean) {
        closeListeningPanel()
        update { it.copy(streaming = enabled, listening = false) }
    }
    fun theme(settings: ThemeSettings) {
        themeStore.save(settings)
        update { it.copy(theme = settings) }
    }
    fun addPhrase(text: String, category: String) {
        quickTexts.add(text, category)
    }
    fun removePhrase(phrase: QuickPhrase) {
        quickTexts.remove(phrase.id)
    }
    fun invoke(action: RegisteredAction) = invoke(action.plugin.descriptor.id, action.action.id)
    fun invoke(pluginId: String, actionId: String, inputs: Map<String, String> = emptyMap()) {
        val plugin = registry.plugin(pluginId) ?: return
        if (pluginId in state.value.busyPlugins) return
        update { it.copy(busyPlugins = it.busyPlugins + pluginId) }
        viewModelScope.launch {
            try {
                val outcome = plugin.execute(actionId, context().copy(inputs = inputs))
                if (handleServiceOutcome(outcome, plugin, pluginManager, context(), { speech.closeListening(); speech.stopAll() },
                        ::refreshPlugins, { selectEngine(it, false) }, ::navigate)) return@launch
                when (outcome) {
                    is PluginOutcome.SelectTtsService -> selectEngine(outcome.pluginId, true)
                    PluginOutcome.RefreshUi -> update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    is PluginOutcome.HostAction -> { check(registry.available(plugin)) { "此插件需要先启用语音识别服务" }; when (outcome.command) {
                        HostCommand.TOGGLE_LISTENING -> toggleListening()
                        HostCommand.CLOSE_LISTENING_PANEL -> closeListeningPanel()
                        HostCommand.USE_STREAMING -> streaming(true)
                        HostCommand.USE_BATCH -> streaming(false)
                        HostCommand.VOICE_INPUT -> speech.voiceInput()
                    } }
                    is PluginOutcome.OpenPage -> {
                        if (plugin.page(outcome.pageId, context()) != null) navigate(Destination.PluginPage(pluginId, outcome.pageId))
                        else message("插件页面不存在")
                    }
                    is PluginOutcome.ReplaceEditorText -> { text(outcome.text); navigate(Destination.Home) }
                    is PluginOutcome.PresentContent -> {
                        val network = when (val media = outcome.content) { is SubtitleContent.Web -> true; is SubtitleContent.Html -> media.allowNetwork; else -> false }
                        check(!network || plugin.descriptor.id in pluginManager.status.filter { "network" in it.installed.manifest.capabilities }.map { it.installed.manifest.id }) { "插件需要声明 network 能力才能加载网络网页" }
                        content.present(pluginId, outcome.content); navigate(Destination.Home)
                    }
                    else -> outcomes.send(pluginId to outcome)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "插件操作失败，请重试") }
            finally { update { it.copy(busyPlugins = it.busyPlugins - pluginId) } }
        }
    }
    fun message(text: String) { viewModelScope.launch { outcomes.send("host" to PluginOutcome.Message(text)) } }

    fun toggleListening() = speech.toggleListening()
    fun speak() { if (state.value.mediaId == null) speech.speak() else message("请切换到文本后朗读") }
    suspend fun refreshPlugins() {
        pluginManager.reload()
        pluginManager.refreshContext(context())
        for (key in listOf("tts", "asr")) {
            val current = engineSelection.getString(key, null)
            val next = chooseEngine(registry.plugins, current, key == "tts")
            if (next != current) engineSelection.edit { putString(key, next) }
        }
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
        if (registry.plugins.none { it.asr?.info?.role == AsrRole.SERVICE }) speech.closeListening()
    }
    fun selectEngine(id: String, tts: Boolean) {
        val plugin = registry.plugin(id) ?: return
        require(if (tts) plugin.tts != null else plugin.asr != null)
        if (!tts && plugin.asr?.info?.role != AsrRole.SERVICE) {
            message("请在语音识别服务中选用识别引擎"); return
        }
        if (tts && plugin.tts?.info?.role != TtsRole.SERVICE && registry.plugins.any { it.tts?.info?.role == TtsRole.SERVICE }) {
            message("请在语音合成服务中选择引擎和声音"); return
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
    fun onBackground() { viewModelScope.launch { speech.stopAll() } }
    override fun onCleared() {
        super.onCleared()
        if (audioSessionDelegate.isInitialized()) cardAudio.release()
        hostRuntime?.detach(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { speech.stopAll(); if (hostRuntime == null) pluginManager.close() }
    }
}
