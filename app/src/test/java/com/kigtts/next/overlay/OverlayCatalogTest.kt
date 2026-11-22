package com.kigtts.next.overlay

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.Destination
import com.kigtts.next.testAppModel
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class OverlayCatalogTest {
    @Test fun personalCardShortcutUsesTheLocalizedTitleAndOpensTheCardPage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val item = overlayBuiltInChoices().single { it.id == "personal-card" }
        assertEquals("个人名片", item.localizedTitle { app.uiCopy(it, locale = "zh-CN") })
        val intent = OverlayLaunch.intent(app, item)
        model.overlayLink(intent)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(Destination.PersonalCards, model.state.value.destination)
        assertNull(OverlayLaunch.consume(intent))
    }
    @Test fun hidingAToolbarButtonDoesNotDisableItsSavedAutomationShortcut() {
        val entry = OverlayShortcut("plugin:agent:action:open", "助手", "smart_toy", "action", owner = "agent", publisher = "key", action = "open", enabled = false)
        val ui = PluginContributions(automationActions = listOf("open"), settingsEntries = listOf(
            SettingsEntry("assistant", "助手", "chat", badge = PluginBadge(tone = BadgeTone.CAUTION))))
        val retained = retainedOverlayActions(listOf(entry), "agent", "key", ui).single()
        assertTrue(retained.enabled); assertEquals("caution", retained.badge?.tone)
        assertTrue(retainedOverlayActions(listOf(entry), "agent", "new-key", ui).isEmpty())
        assertTrue(retainedOverlayActions(listOf(entry), "agent", "key", PluginContributions()).isEmpty())
    }
    @Test fun subtitleMigrationKeepsTheFirstSlotAndDoesNotRestoreRemovedEntries() {
        val quick = OverlayShortcut("quick-text", "快捷文本", "view_agenda", "quick-text")
        val camera = OverlayShortcut("camera", "拍照", "photo_camera", "camera")
        val home = OverlayShortcut("home", "大字幕", "subtitles")
        val migrated = normalizeOverlayShortcuts(listOf(camera, quick, home))
        assertEquals(listOf("camera", "home"), migrated.map { it.id })
        assertEquals("字幕板", migrated.last().title)
        assertEquals(listOf(camera), normalizeOverlayShortcuts(listOf(camera)))
        assertEquals(migrated, normalizeOverlayShortcuts(migrated))
    }
    @Test fun absentAppsAndPluginsStayVisibleAndRecoverOnlyWithTheSamePublisher() {
        val plugin = OverlayShortcut("plugin:p:main", "助手", "smart_toy", owner = "p", publisher = "original", badge = OverlayBadge())
        val app = OverlayShortcut("app:demo", "应用", "apps", component = "demo/.Main")
        val saved = listOf(plugin, app)
        val missing = resolveOverlayShortcuts(saved, emptyList(), emptySet())
        assertEquals(saved.map { it.id }, missing.map { it.id }); assertTrue(missing.none { it.enabled }); assertNull(missing.first().badge)
        assertFalse(resolveOverlayShortcuts(saved, listOf(plugin.copy(publisher = "other")), emptySet()).first().enabled)
        val restored = resolveOverlayShortcuts(missing, listOf(plugin.copy(badge = OverlayBadge(tone = "caution"))), setOf(app.id))
        assertTrue(restored.all { it.enabled }); assertEquals("caution", restored.first().badge?.tone)
    }
    @Test fun manualEntriesFollowOwnerAvailabilityAndLiveBadgeUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hub = OverlayHub.get(context)
        val entry = OverlayShortcut("plugin:catalog:open", "打开", "extension", owner = "catalog", publisher = "key")
        hub.discovered(emptyList(), mapOf("catalog" to "key")); hub.register(entry)
        assertTrue(hub.candidates.value.single { it.id == entry.id }.enabled)
        hub.discovered(emptyList(), emptyMap()); assertFalse(hub.candidates.value.single { it.id == entry.id }.enabled)
        hub.discovered(emptyList(), mapOf("catalog" to "key")); hub.register(entry.copy(badge = OverlayBadge("number", "warning", 120)))
        assertEquals(120, hub.candidates.value.single { it.id == entry.id }.badge?.toPluginBadge()?.count)
        assertEquals(BadgeTone.WARNING, hub.candidates.value.single { it.id == entry.id }.badge?.toPluginBadge()?.tone)
        hub.removeOwner("catalog")
    }
    @Test fun cautionAndThemeBadgesKeepTheirMeaningAndZeroCountsDisappear() {
        assertEquals(BadgeTone.CAUTION, OverlayBadge(tone = "caution").toPluginBadge()?.tone)
        assertEquals(BadgeTone.THEME, OverlayBadge().toPluginBadge()?.tone)
        assertNull(OverlayBadge("number", count = 0).toPluginBadge())
        assertEquals(PluginBadge(124, tone = BadgeTone.CAUTION), PluginBadge(124, tone = BadgeTone.CAUTION).toOverlayBadge().toPluginBadge())
    }
    @Test fun durableReorderingIsACompletePermutation() {
        val store = OverlayStore.get(ApplicationProvider.getApplicationContext<Context>())
        store.update { OverlaySettings() }
        val original = store.settings.value.shortcuts.map { it.id }
        store.order(original.drop(1)); assertEquals(original, store.settings.value.shortcuts.map { it.id })
        store.order(original.reversed()); assertEquals(original.reversed(), store.settings.value.shortcuts.map { it.id })
    }
}
