package com.kigtts.next.sharing

import com.kigtts.next.identity.AvatarFrames
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

/** Descriptors carry private literal addresses only; identity is checked again over HTTP. */
internal fun facePrivateHost(value: String): Boolean = value.length in 3..64 &&
    (value.matches(Regex("[0-9.]+")) || ':' in value && value.matches(Regex("[0-9a-fA-F:%._-]+"))) &&
    runCatching { InetAddress.getByName(value).let { it.isSiteLocalAddress || it.isLinkLocalAddress } }.getOrDefault(false)

internal fun facePeerHosts(peer: FacePeer) = (listOfNotNull(peer.host) + peer.hosts).distinct().take(4)

internal fun facePeerProfile(peer: FacePeer, json: JSONObject): FacePeer {
    require(json.optString("id") == peer.id && json.optInt("protocol") == 1)
    return peer.copy(name = json.optString("nickname").trim().take(40).ifBlank { peer.name },
        avatar = json.optString("avatar").takeIf { it.isNotBlank() && it.length < 65536 },
        avatarFrame = json.optString("avatarFrame").takeIf { it in AvatarFrames.names } ?: "circle",
        profile = json.toString())
}

/** Sparse BLE/NSD updates must not erase an already resolved profile. New tokens discard stale endpoints. */
internal fun mergeFacePeer(old: FacePeer?, peer: FacePeer): FacePeer {
    if (old == null || old.id != peer.id || old.token != peer.token || old.port != peer.port) return peer
    val details = peer.takeIf { it.profile != null } ?: old.takeIf { it.profile != null }
    return peer.copy(name = details?.name ?: peer.name.ifBlank { old.name },
        host = peer.host ?: old.host, hosts = (peer.hosts + old.hosts + listOfNotNull(old.host)).distinct().take(4),
        p2p = peer.p2p ?: old.p2p, bluetooth = peer.bluetooth ?: old.bluetooth,
        signal = maxOf(peer.signal, old.signal), avatar = if (details != null) details.avatar else peer.avatar ?: old.avatar,
        avatarFrame = details?.avatarFrame ?: peer.avatarFrame.takeUnless { it == "circle" } ?: old.avatarFrame,
        profile = details?.profile)
}

internal fun faceBluetoothDescriptor(id: String, profile: JSONObject, port: Int, token: String,
    p2p: String?, hosts: List<String>): JSONObject = JSONObject().put("id", id)
    .put("nickname", profile.optString("nickname").take(40)).put("avatarFrame", profile.optString("avatarFrame", "circle"))
    .put("port", port).put("token", token).put("p2p", p2p ?: "")
    .put("hosts", JSONArray(hosts.filter(::facePrivateHost).distinct().take(4)))

internal fun faceBluetoothPeer(json: JSONObject, bluetooth: String, signal: Int): FacePeer {
    val id = json.getString("id"); val token = json.getString("token"); val port = json.getInt("port")
    require(id.matches(FACE_LAN_TOKEN) && token.matches(FACE_LAN_TOKEN) && port in 1..65535)
    val hosts = json.optJSONArray("hosts")?.let { entries ->
        (0 until minOf(entries.length(), 4)).map { entries.optString(it) }.filter(::facePrivateHost)
    }.orEmpty()
    return FacePeer(id, json.optString("nickname").take(40), port = port, token = token,
        p2p = json.optString("p2p").takeIf { it.matches(Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) && it != "02:00:00:00:00:00" },
        bluetooth = bluetooth, signal = signal, hosts = hosts,
        avatarFrame = json.optString("avatarFrame").takeIf { it in AvatarFrames.names } ?: "circle")
}
