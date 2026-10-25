package com.kigtts.next.sharing

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*

/** Private-network sockets keep temporary HTTP capability URLs out of external proxies and redirects. */
internal object FaceTransferClient {
    private val sockets = java.util.concurrent.ConcurrentHashMap<String, MutableSet<Socket>>()
    fun cancel(owner: String) { sockets.remove(owner)?.forEach { runCatching { it.close() } } }
    suspend fun send(peer: FacePeer, session: String, profile: JSONObject, assets: List<FaceAsset>, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val host = requireNotNull(peer.host); val prefix = "/s/${peer.token}/"
        val id = faceToken()
        val offer = JSONObject().put("id", id).put("session", session).put("profile", profile).put("assets", JSONArray(assets.map { it.json() }))
        request(host, peer.port, "POST", prefix + "offer", offer.toString().toByteArray(Charsets.UTF_8), owner = session)
        var status = "waiting"
        withTimeout(180_000) {
            while (status !in setOf("accepted", "receiving", "complete")) {
                ensureActive(); status = request(host, peer.port, "GET", prefix + "offer/$id", owner = session).optString("status")
                check(status !in setOf("rejected", "error")) { if (status == "rejected") "对方已拒绝接收" else "传输未完成，请重试" }
                if (status == "waiting") delay(700)
            }
        }
        var sent = 0L; val total = assets.sumOf { it.bytes }
        if (status != "complete") for (asset in assets) {
            ensureActive()
            request(host, peer.port, "PUT", prefix + "receive/$id/${asset.id}", file = requireNotNull(asset.file),
                headers = mapOf("X-KIGTTS-Session" to session), owner = session) { bytes -> progress((sent + bytes).toFloat() / total.coerceAtLeast(1)) }
            sent += asset.bytes
        }
        val completed = request(host, peer.port, "GET", prefix + "offer/$id", owner = session).optString("status")
        check(completed == "complete") { "照片未能保存到对方相册，请重试" }; progress(1f)
    }
    fun request(host: String, port: Int, method: String, path: String, body: ByteArray? = null, file: File? = null,
        headers: Map<String, String> = emptyMap(), owner: String? = null, progress: (Long) -> Unit = {}): JSONObject {
        val address = InetAddress.getByName(host)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress)
        require(port in 1..65535 && path.startsWith('/') && '\r' !in path && '\n' !in path)
        return Socket().use { socket ->
            val owned = owner?.let { sockets.computeIfAbsent(it) { java.util.concurrent.ConcurrentHashMap.newKeySet() } }
            owned?.add(socket)
            try {
            socket.connect(InetSocketAddress(address, port), 10_000); socket.soTimeout = 120_000
            val size = file?.length() ?: body?.size?.toLong() ?: 0
            val head = buildString {
                append("$method $path HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\nContent-Length: $size\r\nContent-Type: application/json\r\n")
                headers.forEach { (key, value) -> require('\r' !in value && '\n' !in value); append("$key: $value\r\n") }; append("\r\n")
            }
            val output = BufferedOutputStream(socket.getOutputStream()); output.write(head.toByteArray(Charsets.US_ASCII))
            if (file != null) file.inputStream().use { stream ->
                val buffer = ByteArray(65536); var count = 0L
                while (true) { val read = stream.read(buffer); if (read < 0) break; output.write(buffer, 0, read); count += read; progress(count) }
            } else body?.let(output::write)
            output.flush()
            val input = BufferedInputStream(socket.getInputStream())
            fun line(): String { val buffer = ByteArrayOutputStream(); while (buffer.size() < 4096) { val next = input.read(); if (next < 0) throw EOFException(); if (next == 10) return buffer.toString("US-ASCII").trimEnd('\r'); buffer.write(next) }; error("Response too large") }
            val status = line().split(' ').getOrNull(1)?.toIntOrNull() ?: error("无法连接对方")
            var headerCount = 0; var length = -1
            while (true) { val row = line(); if (row.isEmpty()) break; require(++headerCount < 64)
                if (row.startsWith("Content-Length:", true)) length = row.substringAfter(':').trim().toInt() }
            require(length in 0..65536); val result = ByteArray(length); DataInputStream(input).readFully(result)
            check(status in 200..299) { "连接已结束或对方暂时无法接收" }
            JSONObject(String(result, Charsets.UTF_8))
            } finally { owned?.remove(socket) }
        }
    }
}
