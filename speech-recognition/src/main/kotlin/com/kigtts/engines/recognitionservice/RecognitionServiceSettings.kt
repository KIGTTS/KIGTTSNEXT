package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*
import org.json.JSONObject
import org.json.JSONArray

internal class RecognitionServiceSettings(storage: PluginStorage, private val legacyEnabled: () -> Boolean? = { null }) {
    private val file = storage.file("config/recognition-service.json").also { storage.configuration }
    var provider: String? = null
    var routingDisabled = false
    var resident = false
    var idleSeconds = 30
    var voiceInput = true
    var continuousInput = false
    private var order = emptyList<String>()
    init { reload() }

    fun reload() {
        val json = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        provider = json.optString("provider").takeIf { it.isNotBlank() }
        routingDisabled = if (json.has("routingDisabled")) json.optBoolean("routingDisabled") else legacyEnabled() == false
        resident = json.optBoolean("resident", false)
        idleSeconds = json.optInt("idleSeconds", 30).coerceIn(5, 120)
        voiceInput = json.optBoolean("voiceInput", true)
        continuousInput = json.optBoolean("continuousInput", false)
        order = json.optJSONArray("order")?.let { array ->
            (0 until minOf(array.length(), 512)).mapNotNull { array.opt(it) as? String }
        }.orEmpty()
    }
    fun ordered(providers: List<AsrProvider>): List<AsrProvider> {
        val data = providers.associateBy { it.pluginId }
        return ItemOrder.reconcile(order, providers.sortedBy { it.pluginId }.map { it.pluginId }).mapNotNull(data::get)
    }
    fun current(providers: List<AsrProvider>): AsrProvider? = providers.firstOrNull {
        !routingDisabled && it.pluginId == (provider ?: SYSTEM_RECOGNITION_ID) && it.enabled && it.info.availability == EngineAvailability.READY
    }
    fun reorder(proposed: List<String>, providers: List<AsrProvider>) { ItemOrder.validate(proposed, providers.map { it.pluginId }); order = proposed; save() }
    fun save() { file.writeText(JSONObject().put("provider", provider).put("routingDisabled", routingDisabled).put("order", JSONArray(order))
        .put("voiceInput", voiceInput).put("continuousInput", continuousInput)
        .put("resident", resident).put("idleSeconds", idleSeconds).toString(), Charsets.UTF_8) }
}
