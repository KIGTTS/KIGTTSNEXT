package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.android.AndroidPluginEnvironment
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

class RecognitionServicePlugin(private val legacyEnabled: () -> Boolean? = { null }) : KigPlugin {
    override val descriptor = PluginDescriptor("service.speech-recognition", "语音识别服务", "聆听字幕、语音输入与识别包", version = "0.8.4")
    override val localization get() = RecognitionCopy.localization
    private lateinit var settings: RecognitionServiceSettings
    private lateinit var broker: RecognitionBroker
    private lateinit var facade: ServiceRecognition
    private var system: AndroidSystemRecognition? = null
    override val asr: AsrEngine get() = facade
    fun systemMicrophone(): ((Deferred<Unit>, RecognitionConfig) -> Flow<RecognitionEvent>)? {
        val engine = system ?: return null
        return engine::listen.takeIf { settings.current(broker.providers())?.pluginId == SYSTEM_RECOGNITION_ID }
    }
    override suspend fun attach(environment: PluginEnvironment) {
        settings = RecognitionServiceSettings(environment.storage, legacyEnabled)
        val providers = requireNotNull(environment.recognitionBroker) { "宿主未授予语音识别服务能力" }
        system = (environment as? AndroidPluginEnvironment)?.let { AndroidSystemRecognition(it.context) { it.locale } }
        broker = system?.let { SystemRecognitionBroker(providers, it) } ?: providers
        facade = ServiceRecognition(settings, broker)
    }
    override val contributions get() = contributions(PluginContext("", ""))
    override fun contributions(context: PluginContext): PluginContributions {
        fun t(key: String) = RecognitionCopy.text(key, context.locale)
        val entry = SettingsEntry("manager", t("settings"), "manager", PluginIcon.MIC,
            cardId = "audio", cardTitle = t("audio"), summary = t("settings_summary"))
        val selected = (context.recognitionServiceId == null || context.recognitionServiceId == descriptor.id) &&
            ::facade.isInitialized && facade.info.availability == EngineAvailability.READY
        if (::facade.isInitialized) facade.updateActive(selected)
        return PluginContributions(settingsEntries = listOf(entry), automationActions = listOf("listen", "close", "input", "continuous"), actions = if (selected) listOf(
            UiAction("listen", t(if (captionSession(context)) "close_captions" else "captions"), UiSlot.EDITOR_TOP_START,
                PluginIcon.HEARING, buttonStyle = UiButtonStyle.ICON_TEXT),
            UiAction("close", t("close_captions"), UiSlot.LISTENING_BOTTOM_END, PluginIcon.CLOSE),
            UiAction("continuous", t(if (context.continuousRecognition) "stop_continuous" else "continuous_voice"),
                UiSlot.EDITOR_MENU, PluginIcon.MIC, UiPresentation.MENU_ITEM),
        ) else emptyList(), gestures = if (selected && settings.voiceInput)
            listOf(GestureAction("input", t(if (context.continuousRecognition) "stop_continuous" else "voice_input"), PluginGesture.QUICK_TEXT_LONG_PRESS))
            else emptyList())
    }
    override fun page(pageId: String, context: PluginContext) = recognitionServicePage(pageId, settings, broker, context, descriptor.id)
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        fun t(key: String) = RecognitionCopy.text(key, context.locale)
        val parts = actionId.split('/'); val id = parts.getOrNull(1)
        fun provider() = broker.providers().first { it.pluginId == id }
        return when (parts[0]) {
            "listen" -> if (captionSession(context)) PluginOutcome.EndRecognition() else start(RecognitionPurpose.CAPTIONS, context.locale)
            "close" -> PluginOutcome.EndRecognition()
            "input" -> if (!settings.voiceInput) PluginOutcome.RefreshUi
                else if (context.voiceInputActive || context.continuousRecognition) PluginOutcome.EndRecognition()
                else start(RecognitionPurpose.VOICE_INPUT, context.locale, settings.continuousInput)
            "continuous" -> if (context.continuousRecognition) PluginOutcome.EndRecognition()
                else start(RecognitionPurpose.VOICE_INPUT, context.locale, continuous = true)
            "voice-input" -> { settings.voiceInput = !settings.voiceInput; settings.save(); PluginOutcome.RefreshUi }
            "continuous-input" -> { settings.continuousInput = !settings.continuousInput; settings.save(); PluginOutcome.RefreshUi }
            "disable-recognition" -> {
                settings.routingDisabled = true; settings.save(); facade.settingsChanged(); PluginOutcome.RecognitionRoutingChanged
            }
            "activate" -> PluginOutcome.SelectRecognitionService(descriptor.id)
            "engines" -> PluginOutcome.OpenPage("engines")
            "resident" -> {
                settings.resident = context.inputs["checked"]?.toBooleanStrictOrNull() ?: !settings.resident
                settings.save(); facade.settingsChanged(); PluginOutcome.RefreshUi
            }
            "idle-seconds" -> {
                val value = context.inputs["value"]?.toFloatOrNull()
                require(value != null && value.isFinite() && value in 5f..120f)
                settings.idleSeconds = kotlin.math.round(value).toInt(); settings.save()
                facade.settingsChanged(); PluginOutcome.NoAction
            }
            "select" -> {
                val selected = provider()
                if (selected.pluginId == SYSTEM_RECOGNITION_ID && selected.info.availability != EngineAvailability.READY)
                    return PluginOutcome.Message(t("system_unavailable"))
                settings.provider = selected.pluginId; settings.routingDisabled = false; settings.save(); facade.settingsChanged()
                if (!selected.enabled) PluginOutcome.SetPluginEnabled(selected.pluginId, true) else PluginOutcome.RecognitionRoutingChanged
            }
            "toggle" -> provider().let { PluginOutcome.SetPluginEnabled(it.pluginId, !it.enabled) }
            "settings" -> {
                val selected = provider(); check(selected.enabled) { "请先启用引擎" }
                val page = parts.drop(2).joinToString("/"); require(selected.settingsPages.any { it.id == page })
                PluginOutcome.OpenPluginPage(selected.pluginId, page)
            }
            "reorder" -> { settings.reorder(PluginJson.decodeFromString(requireNotNull(context.inputs["order"])), broker.providers()); PluginOutcome.RefreshUi }
            else -> PluginOutcome.Message(t("unknown_action"))
        }
    }
    override suspend fun close() { if (::facade.isInitialized) facade.close() }
    private fun captionSession(context: PluginContext) = context.listeningPanelVisible ||
        (context.listening && !context.voiceInputActive && !context.continuousRecognition)
    private fun start(purpose: RecognitionPurpose, locale: String, continuous: Boolean = false): PluginOutcome {
        val info = facade.info
        if (info.availability != EngineAvailability.READY) return PluginOutcome.Message(RecognitionCopy.text("choose_ready", locale))
        if ((continuous || purpose == RecognitionPurpose.CAPTIONS) && info.streaming == StreamingKind.NONE)
            return PluginOutcome.Message(RecognitionCopy.text("stream_required", locale))
        return PluginOutcome.StartRecognition(RecognitionSession(descriptor.id, purpose, continuous = continuous,
            output = CaptionOutput.PANEL,
            disableAutomaticRead = continuous, readFinalized = continuous))
    }
}
