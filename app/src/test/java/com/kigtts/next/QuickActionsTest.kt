package com.kigtts.next

import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.quickactions.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class QuickActionsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun lockScreenNotificationDoesNotContainPersonalContentAndCanBeDisabled() {
        val prefs = QuickActionPreferences(app)
        prefs.lockScreenShortcut = true
        LockScreenShortcut.sync(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.single().notification
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(app.packageName, notification.contentIntent.creatorPackage)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        prefs.lockScreenShortcut = false
        LockScreenShortcut.sync(app)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun bluetoothTitleCollapsesNewlinesAndKeepsUnicodeCharactersIntact() {
        assertEquals("字幕 한글 日本語", bluetoothSubtitleTitle(" \n字幕\t한글\u3000日本語 "))
        assertEquals("🚀".repeat(63) + "…", bluetoothSubtitleTitle("🚀".repeat(70)))
        assertEquals("", bluetoothSubtitleTitle("\n\t  "))
    }

    @Test fun lockScreenRouteWaitsUntilTheDeviceIsUnlocked() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val model = AppViewModel(app, listOf(FolderPlugin()))
            val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))
            keyguard.setIsDeviceLocked(true)
            LockScreenShortcut.deliver(app, LockScreenShortcut.mainIntent(app, LockScreenTarget.ALBUM), model)
            assertEquals(Destination.Home, model.state.value.destination)
            keyguard.setIsDeviceLocked(false)
            LockScreenShortcut.deliver(app, LockScreenShortcut.mainIntent(app, LockScreenTarget.ALBUM), model)
            assertEquals(Destination.Cards, model.state.value.destination)
            LockScreenShortcut.deliver(app, LockScreenShortcut.mainIntent(app, LockScreenTarget.SETTINGS), model)
            assertEquals(Destination.Settings, model.state.value.destination)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun gestureTemplatesMatchStrokesAndRejectSwipes() {
        for (gesture in SubtitleGesture.entries) {
            val points = gesture.vertices.zipWithNext().flatMap { (start, end) ->
                (0..7).map { step -> (start + (end - start) * (step / 7f)) * 180f + Offset(13f, 20f) }
            }
            assertEquals(gesture, matchSubtitleGesture(points, 22f))
        }
        assertNull(matchSubtitleGesture(List(20) { Offset(5f * it, 22f) }, 22f))
        assertNull(matchSubtitleGesture(List(20) { Offset(5f * it, 5f * it) }, 22f))
    }

    @Test fun folderActionsRefreshTheCurrentPluginPageWithoutPushingItOntoTheStack() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val plugin = FolderPlugin()
            val model = AppViewModel(app, listOf(plugin))
            val destination = Destination.PluginPage(plugin.descriptor.id, "files")
            model.navigate(destination)
            val history = model.state.value.navigationStack
            var revision = model.state.value.pluginRevision
            model.invoke(plugin.descriptor.id, "create", mapOf("value" to "docs"))
            assertTrue(model.state.value.pluginRevision > revision)
            assertEquals(listOf("docs"), (model.pluginPage(plugin.descriptor.id, "files")?.blocks?.single() as PageBlock.Files).items.map { it.path })
            revision = model.state.value.pluginRevision
            model.invoke(plugin.descriptor.id, "open", mapOf("path" to "docs"))
            assertTrue(model.state.value.pluginRevision > revision)
            assertEquals("docs", (model.pluginPage(plugin.descriptor.id, "files")?.blocks?.single() as PageBlock.Files).directory)
            assertEquals(destination, model.state.value.destination)
            assertEquals(history, model.state.value.navigationStack)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FolderPlugin : KigPlugin {
        override val descriptor = PluginDescriptor("test.folder", "Files", "Editor snapshot fixture")
        private var directory = ""
        private val folders = mutableListOf<PluginFileItem>()

        override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
            when (actionId) {
                "create" -> requireNotNull(context.inputs["value"]).let { folders += PluginFileItem(it, it, true) }
                "open" -> directory = requireNotNull(context.inputs["path"])
            }
            return PluginOutcome.OpenPage("files")
        }

        override fun page(pageId: String, context: PluginContext) = PluginPage(pageId, "Files",
            listOf(PageBlock.Files("Files", if (directory.isEmpty()) folders.toList() else emptyList(), directory, "open", "operate")))
    }
}
