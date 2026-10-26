package com.kigtts.next.audio

import java.net.URLEncoder

/** Device IDs change on reconnect; the saved preference uses the endpoint's identity instead. */
internal data class AudioEndpoint(val key: String, val label: String, val type: Int)
internal fun audioEndpointKey(type: Int, address: String, name: String): String =
    "$type/" + URLEncoder.encode(address.ifBlank { name }, "UTF-8")

internal fun preferredAudioEndpoint(saved: String?, available: List<AudioEndpoint>): AudioEndpoint? =
    available.firstOrNull { it.key == saved }
