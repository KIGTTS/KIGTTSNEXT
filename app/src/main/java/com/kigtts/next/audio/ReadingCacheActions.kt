package com.kigtts.next.audio

import com.kigtts.engines.speechservice.SpeechServicePlugin
import com.kigtts.plugin.api.KigPlugin
import com.kigtts.plugin.api.PluginContext
import com.kigtts.plugin.api.PluginOutcome
import java.util.Base64

/** Cloud accounts can hide their packs while disabled, so use their explicit editing actions. */
internal suspend fun executeWithReadingCache(reading: SpeechServicePlugin?, plugin: KigPlugin,
    action: String, context: PluginContext): PluginOutcome {
    if (reading == null || plugin.descriptor.id != "engine.cloud-tts") return plugin.execute(action, context)
    val parts = action.split('/', limit = 3)
    val encoded = parts.getOrNull(1).orEmpty()
    val affected = when (parts[0]) {
        "remove-pack-confirm" -> runCatching { Base64.getUrlDecoder().decode(encoded).decodeToString() }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let(::setOf).orEmpty()
        "remove-provider-confirm", "save" -> if (parts[0] == "remove-provider-confirm" ||
            parts.getOrNull(2) in setOf("endpoint", "appId", "model")) {
            plugin.page("provider/$encoded", context)?.management?.items.orEmpty().mapTo(mutableSetOf()) { it.id }
        } else emptySet()
        else -> emptySet()
    }
    val result = plugin.execute(action, context)
    if (result is PluginOutcome.OpenPluginPage || result == PluginOutcome.RefreshUi)
        reading.forgetCachedPacks(plugin.descriptor.id, affected)
    return result
}
