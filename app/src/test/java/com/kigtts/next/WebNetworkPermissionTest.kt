package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class WebNetworkPermissionTest {
    @Test fun noExternalConnectionBeforeEntryGrantAndOnceCoversOnlyOneView() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("plugin-permissions", 0).edit().clear().commit()
        val ledger = PluginPermissions(app).apply { foreground = true }; var calls = 0
        val owner = WebNetworkGate.owner("first-entry"); val gate = WebNetworkGate(ledger, owner, "页面", { true })
        val root = File(app.cacheDir, "network-test").apply { mkdirs(); resolve("index.html").writeText("local") }
        val loader = WebResourceLoader(File(root, "cache"), root, true, cookies = object : WebCookieJar { override fun header(url: String): String? = null; override fun receive(url: String, cookies: List<String>) {} }, connectionFactory = { url ->
            calls++
            object : HttpURLConnection(url) {
                override fun disconnect() {}; override fun usingProxy() = false; override fun connect() {}
                override fun getResponseCode() = 200; override fun getContentType() = "text/plain; charset=UTF-8"
                override fun getInputStream() = "external".byteInputStream()
            }
        }, authorizeNetwork = gate::authorize)
        assertEquals("local", loader.load(WebContentPolicy.ORIGIN + "/content/index.html", "GET").data.bufferedReader().readText()); assertEquals(0, calls)
        val request = async(Dispatchers.IO) { loader.load("https://example.com/image.png", "GET").data.bufferedReader().readText() }
        withTimeout(3000) { while (ledger.pending.value.isEmpty()) delay(10) }
        assertEquals(0, calls); assertFalse(request.isCompleted)
        ledger.decide(ledger.pending.value.single().token, PermissionAccess.ASK)
        assertEquals("external", request.await()); assertEquals(1, calls)
        loader.load("https://example.com/style.css", "GET").data.close(); assertEquals(2, calls); assertTrue(ledger.pending.value.isEmpty())
        assertEquals(PermissionAccess.ASK, ledger.access(WebNetworkGate.owner("another-entry"), "network"))
        ledger.set(owner, "network", PermissionAccess.DENY)
        assertEquals(403, loader.load("https://example.com/image.png", "GET").statusCode); assertEquals(2, calls)
        assertFalse(WebNetworkGate(ledger, owner, "页面", { true }).authorize("https://example.com/a"))
    }
}
