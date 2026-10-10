package com.kigtts.next

import com.kigtts.next.media.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebCookieNetworkTest {
    private class Connection(url: URL, val status: Int = 200, val headers: Map<String, List<String>> = emptyMap()) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() { }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderFields() = headers
        override fun getHeaderField(name: String?) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
        override fun getContentType() = "text/html; charset=UTF-8"
        override fun getInputStream() = ByteArrayInputStream("<p>page</p>".toByteArray())
    }
    private class Jar : WebCookieJar {
        val reads = mutableListOf<String>(); val received = mutableListOf<Pair<String, List<String>>>()
        override fun header(url: String): String { reads += url; return "session=active" }
        override fun receive(url: String, cookies: List<String>) { if (cookies.isNotEmpty()) received += url to cookies }
    }
    @Test fun redirectsReceiveCookiesAtEachOriginAndSubresourcesDoNotLeakThemToThirdParties() {
        val cache = File(System.getProperty("java.io.tmpdir"), "cookie-network-" + UUID.randomUUID())
        val jar = Jar(); val connections = mutableListOf<Connection>()
        val loader = WebResourceLoader(cache, null, true, jar, { url ->
            Connection(url, if (url.path == "/start") 302 else 200,
                if (url.path == "/start") mapOf("Location" to listOf("https://second.example/final"), "Set-Cookie" to listOf("first=1"))
                else mapOf("set-cookie" to listOf("a=2; Path=/", "b=3; Secure"))).also { connections += it }
        }, "https://first.example/start")
        try {
            loader.load("https://first.example/start", "GET", true).data.close()
            assertEquals(listOf("https://first.example/start", "https://second.example/final"), jar.reads)
            assertEquals(listOf("first=1"), jar.received.first().second)
            assertEquals(2, jar.received.last().second.size)
            assertEquals("session=active", connections.last().getRequestProperty("Cookie"))
            loader.load("https://second.example/asset", "GET").data.close()
            assertEquals("session=active", connections.last().getRequestProperty("Cookie"))
            val before = jar.received.size
            loader.load("https://third.example/tracker", "GET").data.close()
            assertNull(connections.last().getRequestProperty("Cookie")); assertEquals(before, jar.received.size)
            assertTrue(connections.all { it.disconnected })
        } finally { cache.deleteRecursively() }
    }
    @Test fun localOrDisallowedRequestsNeverReachTheNetworkCookieJar() {
        val directory = File(System.getProperty("java.io.tmpdir"), "cookie-local-" + UUID.randomUUID()).apply { mkdirs() }
        File(directory, "index.html").writeText("local")
        val jar = Jar(); var connections = 0
        val loader = WebResourceLoader(File(directory, "cache"), directory, false, jar, { connections++; Connection(it) })
        try {
            assertEquals("local", loader.load(WebContentPolicy.ORIGIN + "/content/index.html", "GET", true).data.bufferedReader().use { it.readText() })
            assertEquals(403, loader.load("https://example.org/page", "GET").statusCode)
            assertEquals(403, loader.load("bad uri", "GET", true).statusCode)
            assertTrue(jar.reads.isEmpty()); assertTrue(jar.received.isEmpty()); assertEquals(0, connections)
        } finally { directory.deleteRecursively() }
    }
}
