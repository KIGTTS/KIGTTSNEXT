package com.kigtts.next

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.WebAndroidPermissions
import com.kigtts.next.notifications.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.*
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.zip.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PluginNotificationsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var manager: PluginManager
    private val system get() = app.getSystemService(NotificationManager::class.java)
    @Before fun before() {
        manager = PluginManager(app, emptyList())
        manager.paths.packages.deleteRecursively(); manager.paths.packages.mkdirs()
        app.getSharedPreferences("plugin-permissions", 0).edit().clear().commit()
        app.getSharedPreferences("plugin-notification-routes", 0).edit().clear().commit()
        system.cancelAll(); shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    @After fun after() { WebAndroidPermissions.cancel(); system.cancelAll() }
    private fun install(id: String): InstalledPlugin {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val dex = byteArrayOf(1, 2, 3)
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val manifest = PackageManifest(id, id, "1.0", 1, "test.Plugin", files = mapOf("code/classes.dex" to hash(dex)),
            capabilities = listOf("ui", "notifications"), permissionPurposes = mapOf("notifications" to "任务进度与完成提醒"))
        val raw = PluginJson.encodeToString(PackageManifest.serializer(), manifest).toByteArray()
        val file = File.createTempFile("notify", ".zip", app.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            mapOf("plugin.json" to raw, "code/classes.dex" to dex, "signature/public.der" to key.public.encoded,
                "signature/manifest.sig" to Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(raw); sign() }).forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        return try { manager.packages.install(file, hash(key.public.encoded)) } finally { file.delete() }
    }
    private fun grant(item: InstalledPlugin) = manager.permissions.set("${item.manifest.id}:${item.publisherSha256}", "notifications", PermissionAccess.ALLOW)
    @Test fun deniedPluginAndSystemPermissionsNeverPostOrBorrowFullAccess() = runBlocking {
        val item = install("test.notify"); val host = PluginNotifications(manager)
        val command = AppCommand.Notifications("post", PluginNotification("task", "任务"))
        assertEquals("PERMISSION_DENIED", (host.request(item.manifest.id, emptySet(), command) as AppReply.Failure).code)
        manager.automaticPermission = { _, _ -> true }
        manager.permissions.set("${item.manifest.id}:${item.publisherSha256}", "notifications", PermissionAccess.DENY)
        assertEquals("PERMISSION_DENIED", (host.request(item.manifest.id, setOf("notifications"), command) as AppReply.Failure).code)
        grant(item); shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        manager.permissions.foreground = true
        val call = async { host.request(item.manifest.id, setOf("notifications"), command) }
        withTimeout(5000) { WebAndroidPermissions.pending.first { it != null } }
        assertTrue(system.activeNotifications.isEmpty())
        WebAndroidPermissions.finish(emptyMap())
        assertEquals("PERMISSION_DENIED", (call.await() as AppReply.Failure).code)
        assertTrue(system.activeNotifications.isEmpty())
    }
    @Test fun progressCompletionAndTapRoutesRemainOwnerBoundAndSurviveHostRecreation() = runBlocking {
        val first = install("test.first"); val second = install("test.second"); grant(first); grant(second)
        val host = PluginNotifications(manager)
        val task = PluginNotification("task", "正在执行", "读取资料", PluginNotificationKind.PROGRESS, ongoing = true,
            progress = 25, contentAction = PluginNotificationAction("查看", pageId = "chat"), actions = listOf(PluginNotificationAction("停止", actionId = "stop")))
        for (id in listOf(first.manifest.id, second.manifest.id)) assertTrue(host.request(id, setOf("notifications"), AppCommand.Notifications("post", task)) is AppReply.Data)
        assertEquals(2, system.activeNotifications.size)
        val running = system.activeNotifications.single { it.tag.contains("test.first") }.notification
        assertTrue(running.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(25, running.extras.getInt(Notification.EXTRA_PROGRESS))
        val intent = shadowOf(running.contentIntent).savedIntent
        assertEquals(first.manifest.id, NotificationRoutes(app).resolve(intent)!!.owner)
        assertEquals(first.publisherSha256, NotificationRoutes(app).resolve(intent)!!.publisher)
        assertEquals("chat", NotificationRoutes(app).resolve(intent)!!.action.pageId)
        assertNull(NotificationRoutes(app).resolve(Intent(intent).putExtra("notification-token", "00000000-0000-0000-0000-000000000000")))
        val completed = task.copy(title = "已完成", kind = PluginNotificationKind.MESSAGE, ongoing = false, progress = null, actions = emptyList())
        host.request(first.manifest.id, setOf("notifications"), AppCommand.Notifications("post", completed))
        val done = system.activeNotifications.single { it.tag.contains("test.first") }.notification
        assertEquals(0, done.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals("已完成", done.extras.getString(Notification.EXTRA_TITLE))
        assertNull(NotificationRoutes(app).resolve(intent))
        host.cancelAll(first.manifest.id)
        assertEquals(1, system.activeNotifications.size); assertTrue(system.activeNotifications.single().tag.contains("test.second"))
    }
    @Test fun liveUpdatesFallBackToProgressOnOlderSystemsAndRejectShortcutPromotion() = runBlocking {
        val item = install("test.live"); grant(item)
        val host = PluginNotifications(manager)
        val live = PluginNotification("live", "任务进度", kind = PluginNotificationKind.PROGRESS, ongoing = true, live = true, progress = 50)
        assertTrue(host.request(item.manifest.id, setOf("notifications"), AppCommand.Notifications("post", live)) is AppReply.Data)
        val notification = system.activeNotifications.single().notification
        assertEquals(50, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        val invalid = live.copy(kind = PluginNotificationKind.SHORTCUT)
        assertEquals("INVALID", (host.request(item.manifest.id, setOf("notifications"), AppCommand.Notifications("post", invalid)) as AppReply.Failure).code)
        host.request(item.manifest.id, setOf("notifications"), AppCommand.Notifications("cancel", id = "live"))
        assertTrue(system.activeNotifications.isEmpty())
    }
}
