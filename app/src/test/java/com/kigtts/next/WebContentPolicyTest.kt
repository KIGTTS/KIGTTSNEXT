package com.kigtts.next

import com.kigtts.next.media.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebContentPolicyTest {
    @Test fun messagesRequireOwnerMainFrameOriginAndDeclaredAction() {
        val entry = MediaEntry("id", "插件", MediaKind.HTML, "index.html", pluginId = "test.web", actions = listOf("click"))
        val message = "{\"action\":\"click\",\"inputs\":{\"value\":\"你好\"}}"
        assertEquals("click" to mapOf("value" to "你好"), WebContentPolicy.action(entry, message, WebContentPolicy.ORIGIN, true))
        assertNull(WebContentPolicy.action(entry, message, "https://evil.example", true))
        assertNull(WebContentPolicy.action(entry, message, WebContentPolicy.ORIGIN, false))
        assertNull(WebContentPolicy.action(entry.copy(pluginId = null), message, WebContentPolicy.ORIGIN, true))
        assertNull(WebContentPolicy.action(entry, message.replace("click", "uninstall"), WebContentPolicy.ORIGIN, true))
        assertNull(WebContentPolicy.action(entry, "{\"action\":\"click\",\"inputs\":{\"a\":1}}", WebContentPolicy.ORIGIN, true))
    }
    @Test fun filesAreScopedAndOnlyHttpsUrlsAreAccepted() {
        val directory = File(System.getProperty("java.io.tmpdir"), "web-policy-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        try {
            val file = File(directory, "index.html").apply { writeText("hello") }
            assertEquals(file.canonicalFile, WebContentPolicy.file(directory, "index.html"))
            for (path in listOf("../index.html", "/index.html", "a/../../index.html", "C:\\secret", "missing.html")) assertNull(WebContentPolicy.file(directory, path))
        } finally { directory.deleteRecursively() }
        assertTrue(WebContentPolicy.https("https://example.org/path"))
        for (url in listOf("javascript:alert(1)", "file:///tmp/a", "http://example.org", "https://user:pass@example.org")) assertFalse(WebContentPolicy.https(url))
    }
}
