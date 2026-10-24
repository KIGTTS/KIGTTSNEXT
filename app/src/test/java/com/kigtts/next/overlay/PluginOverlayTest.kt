package com.kigtts.next.overlay

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.io.File
import java.security.*
import java.util.zip.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PluginOverlayTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var manager: PluginManager
    @Before fun before() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        manager = PluginManager(app, emptyList())
        manager.paths.packages.deleteRecursively(); manager.paths.packages.mkdirs()
        app.getSharedPreferences("plugin-permissions", 0).edit().clear().commit()
        OverlayStore.get(app).update { OverlaySettings() }
    }
    @After fun after() { ShadowSettings.setCanDrawOverlays(false); Dispatchers.resetMain() }
    private fun install(id: String): InstalledPlugin {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val dex = byteArrayOf(1, 2, 3)
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val manifest = PackageManifest(id, id, "1.0", 1, "test.Plugin", files = mapOf("code/classes.dex" to hash(dex)),
            capabilities = listOf("ui", "overlay"), permissionPurposes = mapOf("overlay" to "快捷入口与任务气泡"))
        val raw = PluginJson.encodeToString(PackageManifest.serializer(), manifest).toByteArray()
        val file = File.createTempFile("overlay", ".zip", app.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            mapOf("plugin.json" to raw, "code/classes.dex" to dex, "signature/public.der" to key.public.encoded,
                "signature/manifest.sig" to Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(raw); sign() }).forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        return try { manager.packages.install(file, hash(key.public.encoded)) } finally { file.delete() }
    }
    private fun grant(item: InstalledPlugin, access: PermissionAccess) = manager.permissions.set("${item.manifest.id}:${item.publisherSha256}", "overlay", access)
    @Test fun fullAccessCannotBypassOverlayPermissionOrManualEntranceConfirmation() = runBlocking {
        val item = install("test.overlay"); val host = PluginOverlayHost(manager)
        val command = AppCommand.Overlay("request-add", OverlayEntrance("camera", "我的相机", "photo_camera", pageId = "camera"))
        manager.automaticPermission = { _, _ -> true }
        grant(item, PermissionAccess.DENY)
        assertEquals("PERMISSION_DENIED", (host.request(item.manifest.id, setOf("overlay"), command) as AppReply.Failure).code)
        grant(item, PermissionAccess.ALLOW)
        assertTrue(host.request(item.manifest.id, setOf("overlay"), command) is AppReply.Data)
        val hub = OverlayHub.get(app); val request = hub.requests.value.single()
        assertEquals(item.manifest.id, request.owner); assertEquals(item.publisherSha256, request.publisher)
        assertEquals(4, OverlayStore.get(app).settings.value.shortcuts.size)
        hub.answer(request.id, true)
        assertEquals(request, OverlayStore.get(app).settings.value.shortcuts.last())
        assertFalse(OverlayStore.get(app).settings.value.enabled)
    }
    @Test fun bubblesRequireUserEnabledOverlayAndCannotBeClearedByAnotherPlugin() = runBlocking {
        val first = install("test.first"); val second = install("test.second"); val host = PluginOverlayHost(manager)
        grant(first, PermissionAccess.ALLOW); grant(second, PermissionAccess.ALLOW)
        val command = AppCommand.Overlay("bubble", bubble = OverlayBubble("执行完成", "查看", pageId = "result"))
        assertEquals("PERMISSION_DENIED", (host.request(first.manifest.id, emptySet(), command) as AppReply.Failure).code)
        assertEquals("OVERLAY_DISABLED", (host.request(first.manifest.id, setOf("overlay"), command) as AppReply.Failure).code)
        OverlayStore.get(app).update { it.copy(enabled = true) }; ShadowSettings.setCanDrawOverlays(true)
        assertTrue(host.request(first.manifest.id, setOf("overlay"), command) is AppReply.Data)
        val hub = OverlayHub.get(app)
        host.request(second.manifest.id, setOf("overlay"), AppCommand.Overlay("clear-bubble"))
        assertEquals(first.manifest.id, hub.bubble.value!!.owner)
        assertEquals(first.publisherSha256, hub.bubble.value!!.shortcut!!.publisher)
        host.removeOwner(first.manifest.id); assertNull(hub.bubble.value)
    }
}
