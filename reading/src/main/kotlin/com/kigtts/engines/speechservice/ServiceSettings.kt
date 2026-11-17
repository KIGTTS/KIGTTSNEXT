// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.PluginStorage
import org.json.JSONObject

internal class ServiceSettings(storage: PluginStorage) {
    private val file = storage.file("config/service.json").also { storage.configuration }
    private val data = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    @Volatile var provider: String? = data.optString("provider").takeIf { it.isNotBlank() }
    @Volatile var voice: String? = data.optString("voice").takeIf { it.isNotBlank() }
    var language: String = data.optString("systemLanguage", "zh-CN")
    var systemVoice: String? = data.optString("systemVoice").takeIf { it.isNotBlank() }
    @Volatile var resident: Boolean = data.optBoolean("resident", false)
    @Volatile var idleMinutes: Int = data.optInt("idleMinutes", DEFAULT_SYNTHESIS_IDLE_MINUTES).coerceIn(1, 15)
    @Volatile var rate: Float = data.optDouble("rate", 1.0).toFloat().takeIf { it.isFinite() && it in .25f..4f } ?: 1f
    @Volatile var cacheEnabled: Boolean = data.optBoolean("cacheEnabled", true)
    init {
        if (provider == LEGACY_SYSTEM_PROVIDER || provider == SYSTEM_PROVIDER) {
            if (systemVoice == null) systemVoice = voice
            provider = null; voice = null
            save()
        }
    }
    fun save() { file.writeText(JSONObject().put("provider", provider.orEmpty()).put("voice", voice.orEmpty()).put("systemVoice", systemVoice.orEmpty()).put("systemLanguage", language).put("rate", rate).put("resident", resident).put("idleMinutes", idleMinutes).put("cacheEnabled", cacheEnabled).toString()) }
}

internal const val DEFAULT_SYNTHESIS_IDLE_MINUTES = 2

/** Old saved selections are migrated to the service-owned platform connection. */
internal const val LEGACY_SYSTEM_PROVIDER = "engine.system-tts"
