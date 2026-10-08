package com.kigtts.plugin.api

enum class UiSlot {
    HEADER_START, HEADER_END,
    EDITOR_TOP_START, EDITOR_TOP_END, EDITOR_MENU, EDITOR_BOTTOM_START, EDITOR_BOTTOM_END,
    LISTENING_BOTTOM_START, LISTENING_BOTTOM_END,
    QUICK_PANEL_START, QUICK_PANEL_END, TAB_START, TAB_END,
}

enum class UiPresentation { BUTTON, MENU_ITEM }
enum class UiButtonStyle { ICON, ICON_TEXT }
enum class PluginIcon { INFO, MIC, SPEAKER, ADD, MENU, SETTINGS, TEXT, STAR, HEARING, CLOSE }

data class UiAction(
    val id: String,
    val label: String,
    val slot: UiSlot,
    val icon: PluginIcon = PluginIcon.INFO,
    val presentation: UiPresentation = UiPresentation.BUTTON,
    val order: Int = 0,
    val buttonStyle: UiButtonStyle = UiButtonStyle.ICON,
)

/** Configuration entries are routed through Settings > Plugins instead of the feature drawer. */
data class DrawerEntry(val id: String, val label: String, val pageId: String, val icon: PluginIcon,
    val settingsPage: Boolean = false)
enum class PluginGesture { QUICK_TEXT_LONG_PRESS }
data class GestureAction(val id: String, val label: String, val gesture: PluginGesture)
data class PluginContributions(
    val actions: List<UiAction> = emptyList(), val drawerEntries: List<DrawerEntry> = emptyList(),
    val gestures: List<GestureAction> = emptyList(),
)

data class PluginPage(val id: String, val title: String, val blocks: List<PageBlock>)
data class PageAction(val id: String, val label: String, val icon: PluginIcon = PluginIcon.INFO)

/** A plugin can build a new page without depending on Compose or an Activity. */
sealed interface PageBlock {
    data class Text(val text: String, val heading: Boolean = false) : PageBlock
    data class Notice(val text: String) : PageBlock
    data class Actions(val actions: List<PageAction>) : PageBlock
    data class Group(val title: String, val children: List<PageBlock>) : PageBlock
}
