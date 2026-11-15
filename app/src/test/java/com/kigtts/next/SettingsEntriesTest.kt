package com.kigtts.next

import com.kigtts.next.state.Destination
import com.kigtts.next.ui.settings.settingsCards
import com.kigtts.next.ui.settings.backupSettingsLinks
import com.kigtts.next.ui.settings.backupSettingsCards
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class SettingsEntriesTest {
    @Test fun iconBadgesReachNativeSettingsAndBackupEntries() {
        val badge = PluginBadge(description = "新增内容", tone = BadgeTone.CAUTION)
        val registry = PluginRegistry(listOf(plugin("test.badges", listOf(
            SettingsEntry("notice", "提醒", "page", badge = badge),
            SettingsEntry("backup", "兼容备份", "page", cardId = "backup", badge = badge)))))
        assertEquals(badge, settingsCards(registry, PluginContext("", "")).flatMap { it.links }.single { it.id.endsWith("/notice") }.badge)
        assertEquals(badge, backupSettingsLinks(registry, PluginContext("", "")).single().badge)
    }
    @Test fun eachCompatibilityFormatGetsItsOwnCardAndKeepsBothActionsTogether() {
        val registry = PluginRegistry(listOf(plugin("backup.kigusay", listOf(
            SettingsEntry("import", "导入娃语", "import", cardId = "backup", cardTitle = "娃语（Kigusay）", order = 1),
            SettingsEntry("export", "导出娃语", "export", cardId = "backup", cardTitle = "娃语（Kigusay）", order = 2))),
            plugin("backup.other", listOf(SettingsEntry("import", "导入其它格式", "import", cardId = "backup", cardTitle = "其它格式")))))
        val cards = backupSettingsCards(registry, PluginContext("", ""))
        assertEquals(2, cards.size)
        assertEquals(listOf("导入娃语", "导出娃语"), cards.single { it.title == "娃语（Kigusay）" }.links.map { it.title })
    }
    private fun plugin(id: String, entries: List<SettingsEntry> = emptyList(), legacy: List<DrawerEntry> = emptyList(),
        requirements: List<PluginRequirement> = emptyList()) = object : KigPlugin {
        override val descriptor = PluginDescriptor(id, id, "", requires = requirements)
        override val contributions = PluginContributions(settingsEntries = entries, drawerEntries = legacy)
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun backupCompatibilityPagesLiveInsideNativeBackupAndAllowNoIcon() {
        val registry = PluginRegistry(listOf(plugin("test.converter", listOf(
            SettingsEntry("legacy", "旧格式兼容", "import", PluginIcon.NONE, cardId = "backup", summary = "转换并预览导入")))))
        val cards = settingsCards(registry, PluginContext("", ""))
        assertTrue(cards.flatMap { it.links }.any { it.destination == Destination.SettingsDetail(com.kigtts.next.state.SettingsCategory.BACKUP_RESTORE) })
        assertFalse(cards.flatMap { it.links }.any { it.title == "旧格式兼容" })
        val format = backupSettingsLinks(registry, PluginContext("", "")).single()
        assertNull(format.icon)
        assertEquals(Destination.PluginPage("test.converter", "import"), format.destination)
    }
    @Test fun formalServiceEntrySharesNativeAudioCardAndDoesNotDuplicateItsLegacyPage() {
        val registry = PluginRegistry(listOf(plugin("test.recognition", listOf(
            SettingsEntry("manager", "语音识别", "manager", PluginIcon.MIC, "audio", "音频", "聆听字幕与语音输入")),
            listOf(DrawerEntry("old-manager", "旧服务入口", "manager", PluginIcon.MIC)))))
        val cards = settingsCards(registry, PluginContext("", ""))
        assertEquals(listOf("sharing", "appearance", "audio", "content", "extensions", "about"), cards.map { it.id })
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
        assertEquals(listOf("外观", "语言与文案", "操作反馈", "悬浮窗", "其它外观"), appearance.links.map { it.title })
        assertEquals("小工具", cards.single { it.id == "utilities" }.title)
        assertEquals("计算器", cards.single { it.id == "utilities" }.links.single().title)
        assertEquals("about", cards.last().id)
        assertEquals("about", cards.last().links.last().id)
    }
    @Test fun legacyPagesMigrateToExtensionsAndKeepConfigurationReachableWithMissingRequirement() {
        val plugin = plugin("test.legacy", legacy = listOf(
            DrawerEntry("feature", "功能", "feature", PluginIcon.TEXT),
            DrawerEntry("settings", "功能设置", "settings", PluginIcon.SETTINGS, settingsPage = true),
            DrawerEntry("duplicate", "重复入口", "settings", PluginIcon.SETTINGS)),
            requirements = listOf(PluginRequirement.RECOGNITION_SERVICE))
        val registry = PluginRegistry(listOf(plugin)); assertFalse(registry.available(plugin))
        val links = settingsCards(registry, PluginContext("", "")).single { it.id == "extensions" }.links
        assertEquals(listOf("功能", "功能设置", "插件管理", "实验性功能"), links.map { it.title })
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
    @Test fun engineEntrancesBelongToServicesWhileManagementPagesRemainReachable() {
        fun engine(id: String, blocks: List<PageBlock>) = object : KigPlugin {
            override val descriptor = PluginDescriptor(id, id, "")
            override val tts = object : TtsEngine { override fun synthesize(request: TtsRequest) = kotlinx.coroutines.flow.emptyFlow<SynthesisEvent>() }
            override val contributions = PluginContributions(drawerEntries = listOf(DrawerEntry("settings", id, "settings", PluginIcon.SPEAKER)))
            override fun page(pageId: String, context: PluginContext) = PluginPage(pageId, id, blocks)
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.NoAction
        }
        val store = plugin("tools.package-store", listOf(SettingsEntry("store", "插件商店", "store", order = 999)))
        val registry = PluginRegistry(listOf(engine("test.about", listOf(PageBlock.Text("说明"))),
            engine("test.engine", listOf(PageBlock.Actions(listOf(PageAction("import", "导入模型"))))), store))
        val cards = settingsCards(registry, PluginContext("", ""))
        assertFalse(cards.flatMap { it.links }.any { it.title == "test.engine" })
        assertFalse(cards.flatMap { it.links }.any { it.title == "test.about" })
        assertEquals(listOf("插件商店", "插件管理", "实验性功能"), cards.single { it.id == "extensions" }.links.map { it.title })
        // Routing hides an entrance, never deletes the engine's actual management page.
        assertNotNull(registry.plugin("test.about")!!.page("settings", PluginContext("", "")))
    }
}
