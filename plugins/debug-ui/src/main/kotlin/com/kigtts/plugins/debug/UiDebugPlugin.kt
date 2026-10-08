package com.kigtts.plugins.debug

import com.kigtts.plugin.api.*

/** UI probes use the public contribution protocol; the host owns their appearance. */
class UiDebugPlugin(
    initialCounts: Map<UiSlot, Int> = emptyMap(),
    private val save: (Map<UiSlot, Int>) -> Unit = {},
    initialStyles: Map<UiSlot, UiButtonStyle> = emptyMap(),
    private val saveStyles: (Map<UiSlot, UiButtonStyle>) -> Unit = {},
) : KigPlugin {
    override val descriptor = PluginDescriptor("debug.ui", "界面调试插件", "在指定扩展区域插入指定数量的按钮或菜单项")
    private val counts = UiSlot.entries.associateWith { initialCounts[it]?.coerceIn(0, 20) ?: 0 }.toMutableMap()
    private val styles = UiSlot.entries.associateWith { initialStyles[it] ?: UiButtonStyle.ICON }.toMutableMap()

    override val contributions: PluginContributions get() = PluginContributions(
        actions = UiSlot.entries.flatMap { slot ->
            List(counts.getValue(slot)) { index ->
                UiAction(slot.name + "/" + index, label(slot) + " · " + (index + 1), slot, PluginIcon.STAR,
                    if (slot == UiSlot.EDITOR_MENU) UiPresentation.MENU_ITEM else UiPresentation.BUTTON, index, styles.getValue(slot))
            }
        },
        drawerEntries = listOf(DrawerEntry("config", "界面调试", "config", PluginIcon.SETTINGS, settingsPage = true)),
    )

    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        if (actionId == "reset") {
            counts.keys.forEach { counts[it] = 0 }; save(counts.toMap())
            return PluginOutcome.RefreshUi
        }
        val parts = actionId.split("/")
        val slot = UiSlot.entries.firstOrNull { it.name == parts.firstOrNull() }
        if (slot != null && parts.size == 2) {
            if (parts[1] == "style") {
                styles[slot] = if (styles.getValue(slot) == UiButtonStyle.ICON) UiButtonStyle.ICON_TEXT else UiButtonStyle.ICON
                saveStyles(styles.toMap())
                return PluginOutcome.RefreshUi
            }
            val delta = when (parts[1]) { "add" -> 1; "remove" -> -1; else -> 0 }
            if (delta != 0) {
                counts[slot] = (counts.getValue(slot) + delta).coerceIn(0, 20)
                save(counts.toMap())
                return PluginOutcome.RefreshUi
            }
            return PluginOutcome.Menu("调试入口 · " + label(slot), listOf(
                PageAction("info", "显示对话框", PluginIcon.INFO), PageAction("config", "打开调试页面", PluginIcon.SETTINGS)))
        }
        return when (actionId) {
            "config" -> PluginOutcome.OpenPage("config")
            "info" -> PluginOutcome.Dialog("调试按钮", "此按钮由调试插件注册，外观和交互由所在区域统一提供。")
            else -> PluginOutcome.Message("未知调试操作")
        }
    }

    override fun page(pageId: String, context: PluginContext) = if (pageId == "config") PluginPage("config", "界面调试", buildList {
        add(PageBlock.Text("扩展区域测试", heading = true))
        add(PageBlock.Text("每个区域可插入 0–20 个入口。默认全部关闭，设置自动保存；按钮继承所在区域的外观。"))
        add(PageBlock.Actions(listOf(PageAction("reset", "清空全部调试入口"))))
        UiSlot.entries.forEach { slot ->
            add(PageBlock.Group(label(slot), listOf(
                PageBlock.Text("当前数量：" + counts.getValue(slot)),
                PageBlock.Text(if (styles.getValue(slot) == UiButtonStyle.ICON) "图标按钮" else "图标文字按钮 · 窄空间自动压缩"),
                PageBlock.Actions(listOf(PageAction(slot.name + "/remove", "减少 · " + label(slot), PluginIcon.MENU),
                    PageAction(slot.name + "/add", "增加 · " + label(slot), PluginIcon.ADD),
                    PageAction(slot.name + "/style", "切换样式 · " + label(slot), PluginIcon.TEXT))),
            )))
        }
    }) else null

    private fun label(slot: UiSlot) = when (slot) {
        UiSlot.HEADER_START -> "顶栏左侧"; UiSlot.HEADER_END -> "顶栏右侧"
        UiSlot.EDITOR_TOP_START -> "字幕左上"; UiSlot.EDITOR_TOP_END -> "字幕右上"
        UiSlot.EDITOR_MENU -> "文本菜单"; UiSlot.EDITOR_BOTTOM_START -> "字幕左下"; UiSlot.EDITOR_BOTTOM_END -> "字幕右下"
        UiSlot.LISTENING_BOTTOM_START -> "聆听面板底部左侧"; UiSlot.LISTENING_BOTTOM_END -> "聆听面板底部右侧"
        UiSlot.QUICK_PANEL_START -> "面板标题左侧"; UiSlot.QUICK_PANEL_END -> "面板标题右侧"
        UiSlot.TAB_START -> "标签开头"; UiSlot.TAB_END -> "标签末尾"
    }
}
