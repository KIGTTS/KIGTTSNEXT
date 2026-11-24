// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.PluginStorage
import org.json.JSONObject

internal class ServiceSettings(storage: PluginStorage) {
    private val file = storage.file("config/service.json").also { storage.configuration }
    @Volatile var provider: String? = null
    @Volatile var voice: String? = null
    var language: String = "zh-CN"
    var systemVoice: String? = null
    @Volatile var resident = false
    @Volatile var idleMinutes = DEFAULT_SYNTHESIS_IDLE_MINUTES
    @Volatile var rate = 1f
    @Volatile var cacheEnabled = true
    init { reload() }

    fun reload() {
        val data = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        provider = data.optString("provider").takeIf { it.isNotBlank() }
        voice = data.optString("voice").takeIf { it.isNotBlank() }
        language = data.optString("systemLanguage", "zh-CN")
        systemVoice = data.optString("systemVoice").takeIf { it.isNotBlank() }
        resident = data.optBoolean("resident", false)
        idleMinutes = data.optInt("idleMinutes", DEFAULT_SYNTHESIS_IDLE_MINUTES).coerceIn(1, 15)
        rate = data.optDouble("rate", 1.0).toFloat().takeIf { it.isFinite() && it in .25f..4f } ?: 1f
        cacheEnabled = data.optBoolean("cacheEnabled", true)
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
