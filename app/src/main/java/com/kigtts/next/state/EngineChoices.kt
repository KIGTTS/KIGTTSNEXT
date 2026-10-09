package com.kigtts.next.state

import com.kigtts.plugin.api.EngineAvailability
import com.kigtts.plugin.api.KigPlugin
import com.kigtts.plugin.api.TtsRole
import com.kigtts.plugin.api.AsrRole

/** Recognition always requires a service; synthesis retains its legacy provider fallback. */
internal fun chooseEngine(plugins: List<KigPlugin>, current: String?, tts: Boolean): String? {
    val eligible = plugins.filter { if (tts) it.tts != null else it.asr != null }
    val services = eligible.filter { if (tts) it.tts?.info?.role == TtsRole.SERVICE else it.asr?.info?.role == AsrRole.SERVICE }
    val candidates = if (tts) services.ifEmpty { eligible } else services
    return candidates.firstOrNull { it.descriptor.id == current }?.descriptor?.id
        ?: (candidates.firstOrNull { if (tts) it.tts?.info?.availability == EngineAvailability.READY
            else it.asr?.info?.availability == EngineAvailability.READY } ?: candidates.firstOrNull())?.descriptor?.id
}
