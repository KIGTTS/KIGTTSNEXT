package com.kigtts.next.audio

import android.media.AudioDeviceInfo
import java.net.URLEncoder

/** Device IDs change on reconnect; the saved preference uses the endpoint's identity instead. */
internal data class AudioEndpoint(val key: String, val label: String, val type: Int)
internal fun audioEndpointKey(type: Int, address: String, name: String): String =
    "$type/" + URLEncoder.encode(address.ifBlank { name }, "UTF-8")

internal fun preferredAudioEndpoint(saved: String?, available: List<AudioEndpoint>): AudioEndpoint? =
    available.firstOrNull { it.key == saved }


// OEMs can expose several ports (bottom, back, etc.) for one built-in microphone route.
// Collapse those only, while keeping distinct external devices even if their product names match.
private val builtInMicPrefix = "${AudioDeviceInfo.TYPE_BUILTIN_MIC}/"
internal fun normalizeAudioEndpointKey(key: String): String =
    if (key.startsWith(builtInMicPrefix)) builtInMicPrefix + "built-in" else key

internal fun labelAudioEndpoints(endpoints: List<AudioEndpoint>): List<AudioEndpoint> {
    val distinct = endpoints.distinctBy { it.key }
    val duplicates = distinct.groupBy { it.label }
    return distinct.map { endpoint ->
        val same = duplicates.getValue(endpoint.label)
        if (same.size == 1) endpoint else endpoint.copy(label = "${endpoint.label} ${same.sortedBy { it.key }.indexOf(endpoint) + 1}")
    }
}
