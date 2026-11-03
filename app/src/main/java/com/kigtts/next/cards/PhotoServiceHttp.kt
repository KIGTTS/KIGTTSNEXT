package com.kigtts.next.cards

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object PhotoServiceHttp {
    suspend fun get(url: String): JSONObject? = PhotoFactsWork.network {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 1500; connection.readTimeout = 1500
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "KIGTTS/1.0 (photo information)")
        try {
            if (connection.responseCode != 200) return@network null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 512 * 1024) {
                    val count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 512 * 1024) null else JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    fun query(base: String, vararg parameters: Pair<String, String>): String = base + "?" + parameters.joinToString("&") {
        URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
    }
}

internal typealias PhotoJsonFetch = suspend (String) -> JSONObject?
