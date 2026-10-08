package com.kigtts.next.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.ThemeSettingsStore
import com.kigtts.plugin.api.*
import com.kigtts.plugins.asr.PlaceholderAsrPlugin
import com.kigtts.plugins.tts.PlaceholderTtsPlugin
import com.kigtts.plugins.debug.UiDebugPlugin
import com.kigtts.plugins.listening.ListeningPanelPlugin
import com.kigtts.plugins.voice.VoiceInputPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import android.icu.text.BreakIterator

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val debugPreferences = application.getSharedPreferences("ui-debug", android.content.Context.MODE_PRIVATE)
    private val voicePreferences = application.getSharedPreferences("voice-input", android.content.Context.MODE_PRIVATE)
    val registry = PluginRegistry(listOf(PlaceholderTtsPlugin(), PlaceholderAsrPlugin(), ListeningPanelPlugin(),
        VoiceInputPlugin(voicePreferences.getBoolean("enabled", false)) { voicePreferences.edit().putBoolean("enabled", it).apply() }, UiDebugPlugin(
        UiSlot.entries.associateWith { debugPreferences.getInt(it.name, 0) },
        { counts -> debugPreferences.edit().apply { counts.forEach { (slot, count) -> putInt(slot.name, count) } }.apply() },
        UiSlot.entries.associateWith { if (debugPreferences.getBoolean(it.name + "_text", false)) UiButtonStyle.ICON_TEXT else UiButtonStyle.ICON },
        { styles -> debugPreferences.edit().apply { styles.forEach { (slot, style) -> putBoolean(slot.name + "_text", style == UiButtonStyle.ICON_TEXT) } }.apply() },
    )))
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
    private var recognitionJob: Job? = null
    private var speechJob: Job? = null
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
    fun closeListeningPanel() {
        recognitionJob?.cancel()
        update { it.copy(captionsVisible = false, listening = false) }
    }
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
        recognitionJob?.cancel()
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
    fun invoke(pluginId: String, actionId: String) {
        val plugin = registry.plugin(pluginId) ?: return
        viewModelScope.launch {
            try {
                when (val outcome = plugin.execute(actionId, context())) {
                    PluginOutcome.RefreshUi -> update { it.copy(pluginRevision = it.pluginRevision + 1) }
                    is PluginOutcome.HostAction -> when (outcome.command) {
                        HostCommand.TOGGLE_LISTENING -> toggleListening()
                        HostCommand.CLOSE_LISTENING_PANEL -> closeListeningPanel()
                        HostCommand.USE_STREAMING -> streaming(true)
                        HostCommand.USE_BATCH -> streaming(false)
                    }
                    is PluginOutcome.OpenPage -> {
                        if (plugin.page(outcome.pageId, context()) != null) navigate(Destination.PluginPage(pluginId, outcome.pageId))
                        else message("插件页面不存在")
                    }
                    is PluginOutcome.ReplaceEditorText -> { text(outcome.text); navigate(Destination.Home) }
                    else -> outcomes.send(pluginId to outcome)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("插件操作失败，请重试") }
        }
    }
    fun message(text: String) { viewModelScope.launch { outcomes.send("host" to PluginOutcome.Message(text)) } }

    fun toggleListening() {
        if (state.value.captionsVisible) { closeListeningPanel(); return }
        val engine = registry.plugins.firstNotNullOfOrNull { it.asr } ?: return
        recognitionJob = viewModelScope.launch {
            update { it.copy(listening = true, captionsVisible = true, transcript = "准备演示字幕…") }
            try {
                if (state.value.streaming) {
                    val demoPcm = flow { repeat(4) { delay(650); emit(PcmAudio(ByteArray(3200))) } }
                    engine.recognizeStream(demoPcm).collect { event ->
                        when (event) {
                            is RecognitionEvent.Partial -> update { it.copy(transcript = event.text) }
                            is RecognitionEvent.Final -> update { it.copy(transcript = event.result.text) }
                            RecognitionEvent.Started -> Unit
                        }
                    }
                } else {
                    val result = engine.recognize(PcmAudio(ByteArray(32000)))
                    update { it.copy(transcript = result.text) }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("聆听演示失败，请重试") }
            finally { update { it.copy(listening = false) } }
        }
    }
    fun speak() {
        if (state.value.speaking) { speechJob?.cancel(); update { it.copy(speaking = false) }; return }
        if (state.value.editorText.isBlank()) { message("请先输入文本"); return }
        val engine = registry.plugins.firstNotNullOfOrNull { it.tts } ?: return
        val request = TtsRequest(state.value.editorText)
        speechJob = viewModelScope.launch {
            update { it.copy(speaking = true) }
            try {
                engine.synthesize(request).collect { event ->
                    if (event is SynthesisEvent.Finished) message(event.notice ?: "朗读完成")
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("朗读演示失败，请重试") }
            finally { update { it.copy(speaking = false) } }
        }
    }
}
