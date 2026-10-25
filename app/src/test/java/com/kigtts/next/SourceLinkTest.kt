package com.kigtts.next

import android.net.Uri
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import com.kigtts.next.plugins.control.SourceLinkCoordinator
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class SourceLinkTest {
    @Test fun packageTargetPersistsAcrossRestartAndOldSameSourceReplyCannotEraseIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val url = "https://example.org/packages.json"
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        val inputs = mutableListOf<Map<String, String>>()
        val coordinator = SourceLinkCoordinator(context, registry(), { _, _, value, done -> inputs += value; callbacks += done }, {})
        coordinator.accept(Uri.parse("kigtts://source/add?url=${Uri.encode(url)}&package=tools.first"))
        coordinator.accept(Uri.parse("kigtts://source/add?url=${Uri.encode(url)}&package=tools.second"))
        assertEquals("tools.second", inputs.last()["packageId"])
        callbacks.first()(true)
        assertEquals("tools.second", prefs.getString("package", null))
        val resumed = SourceLinkCoordinator(context, registry(), { _, _, value, done -> inputs += value; done(true) }, {})
        resumed.deliver()
        assertEquals("tools.second", inputs.last()["packageId"])
        assertNull(prefs.getString("url", null)); assertNull(prefs.getString("package", null))
        assertNull(SourceLinkCoordinator.parseRequest(Uri.parse("kigtts://source/add?url=${Uri.encode(url)}&package=../file")))
        assertNull(SourceLinkCoordinator.parseRequest(Uri.parse("kigtts://source/add?url=${Uri.encode(url)}&package=a&package=b")))
    }
    @Test fun directDetailsAndExplicitSourcePolicySurviveRestart() {
        val url = "https://example.org/frames.json"
        fun link(path: String, suffix: String = "") = Uri.parse("kigtts://source/$path?url=${Uri.encode(url)}&package=test.frames$suffix")
        assertFalse(SourceLinkCoordinator.parseRequest(link("open"))!!.requireSource)
        assertTrue(SourceLinkCoordinator.parseRequest(link("add"))!!.requireSource)
        assertTrue(SourceLinkCoordinator.parseRequest(link("open", "&addSource=required"))!!.requireSource)
        assertFalse(SourceLinkCoordinator.parseRequest(link("add", "&addSource=optional"))!!.requireSource)
        assertNull(SourceLinkCoordinator.parseRequest(link("open", "&addSource=auto")))
        assertNull(SourceLinkCoordinator.parseRequest(link("open", "&addSource=optional&addSource=required")))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefs = app.getSharedPreferences("pending-package-source", 0); prefs.edit().clear().commit()
        SourceLinkCoordinator(app, PluginRegistry(emptyList()), { _, _, _, _ -> error("unused") }, {}).accept(link("open"))
        var inputs: Map<String, String> = emptyMap()
        SourceLinkCoordinator(app, registry(), { _, _, value, done -> inputs = value; done(true) }, {}).deliver()
        assertEquals("false", inputs["requireSource"]); assertEquals("test.frames", inputs["packageId"])
    }
    private fun registry() = PluginRegistry(listOf(object : KigPlugin {
        override val descriptor = PluginDescriptor("test.store", "商店", "test")
        override val contributions = PluginContributions(sourceAction = "add-source")
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }))
    @Test fun busyDeliveryWaitsForIdleAndClearsOnlyAcknowledgedProposal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        var busy = true
        var calls = 0
        var acknowledge: ((Boolean) -> Unit)? = null
        val coordinator = SourceLinkCoordinator(context, registry(), { _, _, _, done -> calls++; acknowledge = done }, {}, { !busy })
        val url = "https://example.org/packages.json"
        val link = Uri.parse("kigtts://source/add?url=" + Uri.encode(url))
        coordinator.accept(link)
        assertEquals(0, calls); assertEquals(url, preferences.getString("url", null))
        busy = false; coordinator.deliver()
        assertEquals(1, calls); assertEquals(url, preferences.getString("url", null))
        acknowledge!!.invoke(false); coordinator.deliver()
        assertEquals("Failed delivery must not loop", 1, calls)
        assertEquals(url, preferences.getString("url", null))
        coordinator.accept(link); acknowledge!!.invoke(true)
        assertEquals(2, calls); assertNull(preferences.getString("url", null))
    }
    @Test fun olderAcknowledgementCannotEraseNewerLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        val coordinator = SourceLinkCoordinator(context, registry(), { _, _, _, done -> callbacks += done }, {})
        coordinator.accept(Uri.parse("kigtts://source/add?url=" + Uri.encode("https://example.org/first.json")))
        coordinator.accept(Uri.parse("kigtts://source/add?url=" + Uri.encode("https://example.org/second.json")))
        callbacks[0](true)
        assertEquals("https://example.org/second.json", preferences.getString("url", null))
        callbacks[1](true); assertNull(preferences.getString("url", null))
    }
    @Test fun sourceLinkAcceptsOnlyExplicitHttpsRepositoryProposal() {
        val url = "https://example.org/packages/catalog.json?branch=stable"
        assertEquals(url, SourceLinkCoordinator.parse(Uri.parse("kigtts://source/add?url=" + Uri.encode(url))))
        listOf("http://example.org/a", "file:///etc/secret", "https://user:password@example.org/a", "https://example.org/a#fragment").forEach {
            assertNull(SourceLinkCoordinator.parse(Uri.parse("kigtts://source/add?url=" + Uri.encode(it))))
        }
        assertNull(SourceLinkCoordinator.parse(Uri.parse("kigtts://source/install?url=" + Uri.encode(url))))
        assertNull(SourceLinkCoordinator.parse(Uri.parse("https://source/add?url=" + Uri.encode(url))))
    }
    @Test fun missingStoreWaitsForStartupAndResumesPersistedLinkAfterInstall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val catalog = PluginRegistry(emptyList())
        var started = false; var prompts = 0; var calls = 0
        val link = Uri.parse("kigtts://source/add?url=" + Uri.encode("https://example.org/plugins.json"))
        val coordinator = SourceLinkCoordinator(context, catalog, { _, _, _, done -> calls++; done(true) }, {},
            ready = { started }, missingStore = { prompts++ })
        coordinator.accept(link); coordinator.deliver()
        assertEquals(0, prompts)
        started = true; coordinator.deliver(); coordinator.deliver()
        assertEquals(1, prompts); assertEquals(0, calls)
        // Cancelling the installer does not discard the proposal; process restart can resume it.
        val resumed = SourceLinkCoordinator(context, catalog, { _, _, _, done -> calls++; done(true) }, {}, missingStore = { prompts++ })
        resumed.deliver(); assertEquals(2, prompts)
        catalog.replace(registry().plugins); resumed.deliver(); resumed.deliver()
        assertEquals(1, calls); assertNull(prefs.getString("url", null))
    }
}
