package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*

internal fun recognitionServicePage(id: String, settings: RecognitionServiceSettings, broker: RecognitionBroker,
    context: PluginContext, serviceId: String): PluginPage? {
    fun t(key: String) = RecognitionCopy.text(key, context.locale)
    fun name(provider: AsrProvider) = if (provider.pluginId == SYSTEM_RECOGNITION_ID) t("system")
        else provider.settingsPages.singleOrNull()?.title?.takeIf(String::isNotBlank) ?: provider.name
    val all = settings.ordered(broker.providers()); val selected = settings.current(all)
    if (id == "manager") return PluginPage(id, t("settings"), buildList {
        if (context.recognitionServiceId != serviceId) add(PageBlock.Actions(listOf(PageAction("activate", t("activate"), PluginIcon.MIC))))
        add(PageBlock.Group(t("recognition"), listOf(PageBlock.Input(t("engine"), selected?.let(::name) ?: t("disabled_short"), "engines", icon = PluginIcon.MIC))))
        add(PageBlock.Group(t("voice_input"), listOf(
            PageBlock.Toggle(t("quick_input"), settings.voiceInput, "voice-input", t("quick_input_hint"), PluginIcon.MIC),
            PageBlock.Toggle(t("continuous"), settings.continuousInput, "continuous-input", t("continuous_hint"), PluginIcon.HEARING))))
        add(PageBlock.Group(t("loading"), listOf(
            PageBlock.Toggle(t("keep_loaded"), settings.resident, "resident", t("keep_loaded_hint")),
            PageBlock.Slider(PluginControl.Slider(t("idle_time"), "idle-seconds", settings.idleSeconds.toFloat(), 5f, 120f, steps = 22, suffix = t("seconds"))))))
        val entries = all.filter { it.enabled }.flatMap { provider -> provider.settingsPages.map { page ->
            PageBlock.Input(page.title, "", "settings/${provider.pluginId}/${page.id}", icon = page.icon)
        } }
        add(PageBlock.Group(t("engine_settings"), entries +
            PageBlock.Input(t("market_engines"), "", "market-engines", symbol = "storefront")))
    })
    if (id == "engines") return PluginPage(id, t("choose_engine"), listOf(PageBlock.Group("", listOf(
        PageBlock.Input(t("market_engines"), "", "market-engines", symbol = "storefront")))),
        ManagementList("recognition-engines", listOf(
            ManagementItem("recognition-disabled", t("disable"), "", PluginIcon.CLOSE, selected = selected == null,
                primaryAction = "disable-recognition", selection = ManagementSelection("disable-recognition"))
        ) + all.sortedBy { it.pluginId != SYSTEM_RECOGNITION_ID }.map { provider ->
            val subtitle = when {
                provider.pluginId == SYSTEM_RECOGNITION_ID -> if (provider.info.availability == EngineAvailability.READY)
                    t("system_hint") else t("system_unavailable")
                !provider.enabled -> t("off")
                provider.info.availability == EngineAvailability.READY -> t(recognitionKindKey(provider.info.streaming))
                else -> t("needs_setup")
            }
            ManagementItem(provider.pluginId, name(provider),
                subtitle,
                PluginIcon.MIC, selected = provider.pluginId == selected?.pluginId,
                primaryAction = "select/${provider.pluginId}", selection = ManagementSelection("select/${provider.pluginId}"))
        }, emptyText = t("empty")))
    return null
}
private fun recognitionKindKey(kind: StreamingKind) = when (kind) {
    StreamingKind.INCREMENTAL -> "incremental"
    StreamingKind.SEGMENTED -> "segmented"
    StreamingKind.NONE -> "batch"
}
