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

class AppViewModel @JvmOverloads constructor(application: Application, engineFixtures: List<KigPlugin> = emptyList(),
    audio: AudioPlatform = AndroidAudioPlatform(application)) : AndroidViewModel(application) {
    val pluginManager = PluginManager(application, engineFixtures + builtinPlugins(application))
    val registry = pluginManager.registry
    private val engineSelection = application.getSharedPreferences("engine-selection", android.content.Context.MODE_PRIVATE)
    val selectedTtsId get() = engineSelection.getString("tts", null)
    val selectedAsrId get() = engineSelection.getString("asr", null)
    private val themeStore = ThemeSettingsStore(application)
    private val feedbackStore = FeedbackSettingsStore(application)
    private val phraseStore = QuickPhraseStore(application)
    private val panelStore = HomePanelStore(application)
    private val subtitleSizeStore = SubtitleSizeStore(application)
    private val mutableState = MutableStateFlow(AppState(theme = themeStore.load(), hapticsEnabled = feedbackStore.load(),
        lastExpandedPanelHeight = panelStore.loadHeight(), subtitleSizePercent = subtitleSizeStore.load()))
    val state = mutableState.asStateFlow()
    val quickTexts = QuickTextLibrary(phraseStore)
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
        state.map { PluginContext(it.editorText, it.transcript, it.listening, it.captionsVisible) }
            .distinctUntilChanged().debounce(100).collect { context ->
                if (pluginManager.refreshContext(context)) update { it.copy(pluginRevision = it.pluginRevision + 1) }
            }
    }
    init { if (engineFixtures.isEmpty()) observePluginContext() }
    private val navigationHistory = ArrayDeque<Destination>()

    private fun update(transform: (AppState) -> AppState) { mutableState.update(transform) }
    fun context() = PluginContext(state.value.editorText, state.value.transcript, state.value.listening, state.value.captionsVisible)
    fun navigate(destination: Destination) {
        if (destination == Destination.Home) navigationHistory.clear()
        else if (state.value.destination != destination) navigationHistory.addLast(state.value.destination)
        update { it.copy(destination = destination, navigationStack = navigationHistory.toList(), editing = false) }
    }
    fun navigateBack() {
        val previous = if (navigationHistory.isEmpty()) Destination.Home else navigationHistory.removeLast()
        update { it.copy(destination = previous, navigationStack = navigationHistory.toList(), editing = false) }
    }
    fun haptics(enabled: Boolean) {
        feedbackStore.save(enabled)
        update { it.copy(hapticsEnabled = enabled) }
    }
    fun openPanel(panel: HomePanel, dragHeight: Float? = null) {
        update { it.copy(destination = Destination.Home, panel = panel, panelHeight = dragHeight ?: it.lastExpandedPanelHeight) }
    }
    fun openSidePage(page: SidePage) { update { it.copy(destination = Destination.Home, sidePage = page, editing = false) } }
    fun closeSidePage() { update { it.copy(sidePage = null) } }
    fun closePanel() { update { it.copy(panel = null, quickTextEditing = false) } }
    fun editQuickTexts() { update { it.copy(panel = HomePanel.QUICK_TEXT, quickTextEditing = true, editing = false, forcedFullscreen = false) } }
    fun finishQuickTextEditing() { update { it.copy(quickTextEditing = false) } }
    fun panelHeight(height: Float) {
        val value = height.coerceAtLeast(HomePanelSizes.COLLAPSED)
        if (value > HomePanelSizes.COLLAPSED) panelStore.saveHeight(value)
        update { it.copy(panelHeight = value,
            lastExpandedPanelHeight = if (value > HomePanelSizes.COLLAPSED) value else it.lastExpandedPanelHeight) }
    }
    fun closeListeningPanel() = speech.closeListening()
    fun captionHeight(height: Float) { update { it.copy(captionHeight = height) } }
    fun editing(editing: Boolean) { update { it.copy(editing = editing) } }
    fun enterSubtitleFullscreen() { update { it.copy(forcedFullscreen = true) } }
    fun exitSubtitleFullscreen() { update { it.copy(editing = false, forcedFullscreen = false) } }
    fun text(text: String) { update { it.copy(editorValue = TextFieldValue(text, TextRange(text.length)), subtitleRevision = it.subtitleRevision + 1) } }
    fun editText(value: TextFieldValue) { update { it.copy(editorValue = value) } }
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
                when (val outcome = plugin.execute(actionId, context().copy(inputs = inputs))) {
                    is PluginOutcome.SelectTtsService -> selectEngine(outcome.pluginId, true)
                    PluginOutcome.RefreshUi -> update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    is PluginOutcome.HostAction -> when (outcome.command) {
                        HostCommand.TOGGLE_LISTENING -> toggleListening()
                        HostCommand.CLOSE_LISTENING_PANEL -> closeListeningPanel()
                        HostCommand.USE_STREAMING -> streaming(true)
                        HostCommand.USE_BATCH -> streaming(false)
                        HostCommand.VOICE_INPUT -> speech.voiceInput()
                    }
                    is PluginOutcome.OpenPage -> {
                        if (plugin.page(outcome.pageId, context()) != null) navigate(Destination.PluginPage(pluginId, outcome.pageId))
                        else message("插件页面不存在")
                    }
                    is PluginOutcome.ReplaceEditorText -> { text(outcome.text); navigate(Destination.Home) }
                    else -> outcomes.send(pluginId to outcome)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "插件操作失败，请重试") }
            finally { update { it.copy(busyPlugins = it.busyPlugins - pluginId) } }
        }
    }
    fun message(text: String) { viewModelScope.launch { outcomes.send("host" to PluginOutcome.Message(text)) } }

    fun toggleListening() = speech.toggleListening()
    fun speak() = speech.speak()
    suspend fun refreshPlugins() {
        pluginManager.reload()
        for (key in listOf("tts", "asr")) {
            val current = engineSelection.getString(key, null)
            val next = chooseEngine(registry.plugins, current, key == "tts")
            if (next != current) engineSelection.edit { putString(key, next) }
        }
        update { it.copy(pluginRevision = it.pluginRevision + 1) }
    }
    fun selectEngine(id: String, tts: Boolean) {
        val plugin = registry.plugin(id) ?: return
        require(if (tts) plugin.tts != null else plugin.asr != null)
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
        viewModelScope.launch {
            try {
                speech.stopAll()
                if (enabled == null) pluginManager.uninstall(id) else pluginManager.enable(id, enabled)
                refreshPlugins()
            } catch (error: Exception) { message(error.message ?: "插件操作失败") }
        }
    }
    suspend fun stopSpeech() = speech.stopAll()
    fun onBackground() { viewModelScope.launch { speech.stopAll() } }
    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { speech.stopAll(); pluginManager.close() }
    }
}
