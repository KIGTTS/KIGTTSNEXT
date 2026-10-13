package com.kigtts.next

import com.kigtts.next.state.Destination
import com.kigtts.next.ui.settings.settingsCards
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class SettingsEntriesTest {
    private fun plugin(id: String, entries: List<SettingsEntry> = emptyList(), legacy: List<DrawerEntry> = emptyList(),
        requirements: List<PluginRequirement> = emptyList()) = object : KigPlugin {
        override val descriptor = PluginDescriptor(id, id, "", requires = requirements)
        override val contributions = PluginContributions(settingsEntries = entries, drawerEntries = legacy)
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun formalServiceEntrySharesNativeAudioCardAndDoesNotDuplicateItsLegacyPage() {
        val registry = PluginRegistry(listOf(plugin("test.recognition", listOf(
            SettingsEntry("manager", "语音识别", "manager", PluginIcon.MIC, "audio", "音频", "聆听字幕与语音输入")),
            listOf(DrawerEntry("old-manager", "旧服务入口", "manager", PluginIcon.MIC)))))
        val cards = settingsCards(registry, PluginContext("", ""))
        assertEquals(listOf("appearance", "audio", "content", "extensions"), cards.map { it.id })
        val audio = cards.single { it.id == "audio" }
        assertEquals("音频", audio.title); assertEquals(1, audio.links.size)
        assertEquals("语音识别", audio.links.single().title)
        assertEquals(Destination.PluginPage("test.recognition", "manager"), audio.links.single().destination)
        assertTrue(cards.flatMap { it.links }.none { it.title == "旧服务入口" })
    }
    @Test fun reservedCardTitleStaysHostOwnedAndCustomCardsCanBeAdded() {
        val registry = PluginRegistry(listOf(plugin("test.tools", listOf(
            SettingsEntry("theme", "其它外观", "theme", cardId = "appearance", cardTitle = "替换宿主标题"),
            SettingsEntry("tools", "计算器", "tools", cardId = "utilities", cardTitle = "小工具")))))
        val cards = settingsCards(registry, PluginContext("", ""))
        val appearance = cards.single { it.id == "appearance" }
        assertEquals("外观与交互", appearance.title)
        assertEquals(listOf("外观", "触感反馈", "其它外观"), appearance.links.map { it.title })
        assertEquals("小工具", cards.last().title); assertEquals("计算器", cards.last().links.single().title)
    }
    @Test fun legacyPagesMigrateToExtensionsAndKeepConfigurationReachableWithMissingRequirement() {
        val plugin = plugin("test.legacy", legacy = listOf(
            DrawerEntry("feature", "功能", "feature", PluginIcon.TEXT),
            DrawerEntry("settings", "功能设置", "settings", PluginIcon.SETTINGS, settingsPage = true),
            DrawerEntry("duplicate", "重复入口", "settings", PluginIcon.SETTINGS)),
            requirements = listOf(PluginRequirement.RECOGNITION_SERVICE))
        val registry = PluginRegistry(listOf(plugin)); assertFalse(registry.available(plugin))
        val links = settingsCards(registry, PluginContext("", "")).single { it.id == "extensions" }.links
        assertEquals(listOf("实验性功能", "插件管理", "功能", "功能设置"), links.map { it.title })
        assertEquals(2, links.count { it.destination is Destination.PluginPage })
    }
    @Test fun featureOrderAndContextChangesAreReflectedWithoutNativeEntriesBeingReplaced() {
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.dynamic", "动态入口", "")
            override fun contributions(context: PluginContext) = PluginContributions(settingsEntries = listOf(
                SettingsEntry("second", if (context.voiceInputActive) "正在输入" else "语音输入", "input", cardId = "audio", order = 2),
                SettingsEntry("first", "语音包", "voices", cardId = "audio", order = 1)))
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val registry = PluginRegistry(listOf(plugin))
        val normal = settingsCards(registry, PluginContext("", "")).single { it.id == "audio" }.links
        val active = settingsCards(registry, PluginContext("", "", voiceInputActive = true)).single { it.id == "audio" }.links
        assertEquals(listOf("语音包", "语音输入"), normal.map { it.title })
        assertEquals(listOf("语音包", "正在输入"), active.map { it.title })
        assertEquals(normal.map { it.id }, active.map { it.id })
    }
}
