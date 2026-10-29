package com.kigtts.next.sharing

import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress

internal val FACE_LAN_TOKEN = Regex("[A-Za-z0-9_-]{32,64}")
internal const val FACE_LAN_PORT = 44802
internal const val FACE_LAN_PACKET_LIMIT = 1024
private const val PROTOCOL = "KIGTTS-FACE/1"

/** A small local query/response, never a URL, remote host name, or photo payload. */
internal object FaceLanProtocol {
    fun query(id: String): ByteArray = JSONObject().put("protocol", PROTOCOL).put("kind", "query").put("id", id)
        .toString().toByteArray(Charsets.UTF_8)

    fun response(self: FaceLanIdentity, to: String): ByteArray = JSONObject().put("protocol", PROTOCOL).put("kind", "peer")
        .put("to", to).put("id", self.id).put("name", self.name.take(40)).put("port", self.port).put("key", self.token)
        .toString().toByteArray(Charsets.UTF_8)

    fun parse(bytes: ByteArray, length: Int, source: InetAddress): JSONObject? {
        if (length !in 1..minOf(bytes.size, FACE_LAN_PACKET_LIMIT) || source !is Inet4Address ||
            !(source.isSiteLocalAddress || source.isLinkLocalAddress || source.isLoopbackAddress)) return null
        val json = runCatching { JSONObject(String(bytes, 0, length, Charsets.UTF_8)) }.getOrNull() ?: return null
        return json.takeIf { it.optString("protocol") == PROTOCOL && it.optString("id").matches(FACE_LAN_TOKEN) }
    }

    fun peer(json: JSONObject, source: InetAddress, self: String): FacePeer? {
        val id = json.optString("id"); val token = json.optString("key"); val port = json.optInt("port")
        if (json.optString("kind") != "peer" || json.optString("to") != self || id == self ||
            !id.matches(FACE_LAN_TOKEN) || !token.matches(FACE_LAN_TOKEN) || port !in 1..65535) return null
        return FacePeer(id, json.optString("name").take(40), source.hostAddress, port, token)
    }
}
