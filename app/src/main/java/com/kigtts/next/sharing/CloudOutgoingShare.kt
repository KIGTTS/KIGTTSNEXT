package com.kigtts.next.sharing

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** Only the chosen share and its owner credential are kept until the session ends. */
internal class CloudOutgoingShare(val panel: CloudNativePanel, private val root: File) {
    private val config = JSONObject(File(root, "config.json").readText())
    private val room = config.getString("id").also { require(it.matches(Regex("[A-Za-z0-9_-]{22}"))) }
    private val credential = config.getString("owner").also { require(it.matches(Regex("[A-Za-z0-9_-]{43}"))) }
    val expiresAt = config.getLong("expires")
    val key get() = panel.pageId

    fun release() {
        try {
            val connection = URI("https://app.kigtts.lhtstudio.com/api/rooms/$room").toURL()
                .openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.requestMethod = "DELETE"
                connection.setRequestProperty("Authorization", "Bearer $credential")
                connection.responseCode
            } finally { connection.disconnect() }
        } finally { root.deleteRecursively() }
    }
}
