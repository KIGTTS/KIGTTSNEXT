package com.kigtts.next.media

import android.webkit.WebResourceResponse
import android.webkit.MimeTypeMap
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.security.MessageDigest
import androidx.core.net.toUri

/** WebView never fetches resources outside this loader. Network copies belong to the content owner. */
class WebResourceLoader(private val directory: File, private val local: File?, private val network: Boolean,
    private val cookies: WebCookieJar = WebCookies,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    pageOrigin: String? = null, private val bundledResource: (String) -> WebResourceResponse? = { null },
    private val authorizeNetwork: (String) -> Boolean = { true }, private val networkGranted: () -> Boolean = { network }) {
    @Volatile private var siteHost: String? = pageOrigin?.let { URI(it).host }
    fun load(url: String, method: String, mainFrame: Boolean = false): WebResourceResponse {
        bundledResource(url)?.let { return it }
        if (mainFrame) siteHost = runCatching { URI(url).host }.getOrNull()
        if (url.startsWith(WebContentPolicy.ORIGIN + "/content/")) {
            val path = url.toUri().path.orEmpty().removePrefix("/content/")
            val file = local?.let { WebContentPolicy.file(it, path) } ?: return denied()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            return WebResourceResponse(mime, if (mime.startsWith("text/")) "UTF-8" else null, 200, "OK",
                mapOf("Content-Security-Policy" to webNetworkCsp(network && networkGranted()), "Permissions-Policy" to "accelerometer=(), gyroscope=(), magnetometer=()"), file.inputStream())
        }
        // Favicon probes and unknown bundled paths are local misses, never external network requests.
        if (WebContentPolicy.internal(url)) return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        if (!network || method != "GET" || !WebContentPolicy.https(url)) return denied()
        if (!authorizeNetwork(url)) return denied()
        return runCatching {
            var address = url; var connection: HttpURLConnection? = null
            for (attempt in 0..5) {
                val response = connectionFactory(URL(address)); connection = response
                response.apply { instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 20_000; useCaches = false }
                val acceptsCookies = mainFrame || siteHost == null || URI(address).host == siteHost
                if (acceptsCookies) cookies.header(address)?.takeIf(String::isNotBlank)?.let { response.setRequestProperty("Cookie", it) }
                val status = response.responseCode
                if (mainFrame) siteHost = URI(address).host
                if (acceptsCookies) cookies.receive(address, response.headerFields.entries.filter { it.key?.equals("Set-Cookie", true) == true }.flatMap { it.value.orEmpty() })
                if (status !in setOf(301, 302, 303, 307, 308)) break
                val redirected = URL(URL(address), connection.getHeaderField("Location")).toString(); connection.disconnect()
                require(WebContentPolicy.https(redirected)); require(attempt < 5); address = redirected
            }
            val request = requireNotNull(connection)
            try {
                val type = request.contentType.orEmpty(); val mime = type.substringBefore(';').ifBlank { "application/octet-stream" }
                val encoding = type.substringAfter("charset=", "UTF-8").substringBefore(';').trim()
                if (request.responseCode !in 200..299) return@runCatching denied()
                directory.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
                val temporary = File.createTempFile("resource-", ".tmp", directory)
                try {
                    request.inputStream.use { input -> temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024); var total = 0L
                        while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 32L * 1024 * 1024); output.write(buffer, 0, count) }
                    } }
                    val destination = File(directory, digest)
                    java.nio.file.Files.move(temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    // Bound persistent cache; actual requests are freshly fetched, so stale resources cannot cross sessions.
                    var bytes = directory.listFiles().orEmpty().sumOf { it.length() }
                    directory.listFiles().orEmpty().sortedBy { it.lastModified() }.forEach { if (bytes > 128L * 1024 * 1024 && it != destination) { bytes -= it.length(); it.delete() } }
                    val headers = request.headerFields.entries.filter { it.key != null && it.key.lowercase() in setOf("access-control-allow-origin", "access-control-allow-credentials", "access-control-expose-headers", "content-language") }
                        .associate { it.key!! to it.value.joinToString(", ") } + mapOf("Content-Security-Policy" to webNetworkCsp(network && networkGranted()), "Permissions-Policy" to "accelerometer=(), gyroscope=(), magnetometer=()")
                    WebResourceResponse(mime, encoding, 200, "OK", headers, destination.inputStream())
                } finally { temporary.delete() }
            } finally { request.disconnect() }
        }.getOrElse { denied() }
    }
    /** WebView owns non-GET request bodies; permission is checked before allowing its native transport. */
    fun intercept(url: String, method: String, mainFrame: Boolean = false): WebResourceResponse? =
        if (method != "GET" && !WebContentPolicy.internal(url) && network && WebContentPolicy.https(url) && authorizeNetwork(url)) null else load(url, method, mainFrame)
    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Unavailable", emptyMap(), ByteArrayInputStream("资源不可用".toByteArray()))
}

internal fun webNetworkCsp(allowed: Boolean): String = "connect-src 'self'" + (if (allowed) " https: wss:" else "") + "; worker-src 'none'; object-src 'none'; base-uri 'self'; form-action " + (if (allowed) "'self' https:" else "'self'")
