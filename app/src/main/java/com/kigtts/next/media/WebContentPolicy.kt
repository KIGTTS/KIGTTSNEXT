package com.kigtts.next.media

import org.json.JSONObject
import java.io.File
import java.net.URI

object WebContentPolicy {
    const val ORIGIN = "https://appassets.androidplatform.net"
    fun internal(url: String) = runCatching { URI(url).let {
        it.scheme == "https" && it.host.equals("appassets.androidplatform.net", true) && it.port in setOf(-1, 443) && it.userInfo == null
    } }.getOrDefault(false)
    fun https(url: String) = runCatching { URI(url).let { it.scheme == "https" && it.host != null && it.userInfo == null } }.getOrDefault(false)
    fun file(root: File, path: String): File? = runCatching {
        require(path.isNotEmpty() && !path.contains('\\') && !path.contains(':') && !path.startsWith('/'))
        require(path.split('/').none { it == ".." || it == "." })
        File(root, path).canonicalFile.takeIf { it.toPath().startsWith(root.canonicalFile.toPath()) && it.isFile }
    }.getOrNull()
    fun action(entry: MediaEntry, data: String, origin: String, mainFrame: Boolean): Pair<String, Map<String, String>>? = runCatching {
        require(mainFrame && origin == ORIGIN && entry.kind == MediaKind.HTML && entry.pluginId != null && data.length <= 64 * 1024)
        val json = JSONObject(data); val action = json.getString("action"); require(action in entry.actions)
        val input = json.optJSONObject("inputs") ?: JSONObject(); require(input.length() <= 32)
        action to input.keys().asSequence().associateWith { key -> require(key.length <= 128); input.get(key).also { require(it is String && it.length <= 4096) }.toString() }
    }.getOrNull()
}
