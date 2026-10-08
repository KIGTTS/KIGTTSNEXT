package com.kigtts.next.state

import com.kigtts.plugin.api.EngineAvailability
import com.kigtts.plugin.api.KigPlugin
import com.kigtts.plugin.api.TtsRole

/** A service owns TTS routing while it is enabled. ASR selection remains independent. */
internal fun chooseEngine(plugins: List<KigPlugin>, current: String?, tts: Boolean): String? {
    val eligible = plugins.filter { if (tts) it.tts != null else it.asr != null }
    val services = if (tts) eligible.filter { it.tts?.info?.role == TtsRole.SERVICE } else emptyList()
    val candidates = services.ifEmpty { eligible }
    return candidates.firstOrNull { it.descriptor.id == current }?.descriptor?.id
        ?: (candidates.firstOrNull { if (tts) it.tts?.info?.availability == EngineAvailability.READY
            else it.asr?.info?.availability == EngineAvailability.READY } ?: candidates.firstOrNull())?.descriptor?.id
}
