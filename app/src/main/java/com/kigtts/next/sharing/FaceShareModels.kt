package com.kigtts.next.sharing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal fun faceToken(): String = ByteArray(24).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
internal fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { hash ->
    file.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { val count = stream.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) } }
    hash.digest().joinToString("") { "%02x".format(it) }
}
internal data class FaceAsset(val id: String, val photo: String, val variant: String, val name: String,
    val mime: String, val bytes: Long, val sha: String, val format: String, val file: File? = null) {
    fun json() = JSONObject().put("id", id).put("photo", photo).put("variant", variant).put("name", name)
        .put("mime", mime).put("bytes", bytes).put("sha256", sha).put("format", format)
    companion object {
        fun parse(json: JSONObject): FaceAsset {
            val asset = FaceAsset(json.getString("id"), json.getString("photo"), json.getString("variant"), json.getString("name"),
                json.getString("mime"), json.getLong("bytes"), json.getString("sha256"), json.optString("format", "SQUARE"))
            require(asset.id.matches(Regex("[A-Za-z0-9_-]{8,64}")) && asset.photo.matches(Regex("[A-Za-z0-9_-]{8,64}")))
            require(asset.variant in setOf("original", "polaroid", "profile") && asset.mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif"))
            require(asset.bytes in 1..268_435_456 && asset.sha.matches(Regex("[a-f0-9]{64}")) && asset.name.length <= 128)
            require(asset.format in setOf("MINI", "SQUARE", "WIDE", "LEGACY", "BANNER", "PROFILE"))
            require((asset.format == "PROFILE") == (asset.variant == "profile"))
            if (asset.format == "PROFILE") require(asset.bytes <= 20_000_000)
            return asset
        }
    }
}
internal data class FacePeer(val id: String, val name: String, val host: String? = null, val port: Int,
    val token: String, val p2p: String? = null, val bluetooth: String? = null, val signal: Int = -100, val avatar: String? = null,
    val profile: String? = null)
internal data class FaceOffer(val id: String, val session: String, val profile: JSONObject, val assets: List<FaceAsset>,
    val created: Long = System.currentTimeMillis(), val status: String = "waiting") {
    val nickname get() = profile.optString("nickname", "KIGTTS 用户").take(40)
}
internal enum class FaceMode { BROWSER, SEND, RECEIVE }
internal data class FacePanel(val ids: List<String>, val mode: FaceMode = FaceMode.SEND, val personal: Boolean = false)
internal fun assetsFrom(json: JSONObject): List<FaceAsset> {
    val list = json.getJSONArray("assets"); require(list.length() in 1..128)
    return (0 until list.length()).map { FaceAsset.parse(list.getJSONObject(it)) }.also { assets ->
        require(assets.map { it.id }.distinct().size == assets.size && assets.sumOf { it.bytes } <= 4L * 1024 * 1024 * 1024)
    }
}
