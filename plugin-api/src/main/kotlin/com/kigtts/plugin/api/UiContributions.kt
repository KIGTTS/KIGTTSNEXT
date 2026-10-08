package com.kigtts.plugin.api

import kotlinx.serialization.Serializable

@Serializable
enum class UiSlot {
    HEADER_START, HEADER_END,
    EDITOR_TOP_START, EDITOR_TOP_END, EDITOR_MENU, EDITOR_BOTTOM_START, EDITOR_BOTTOM_END,
    LISTENING_BOTTOM_START, LISTENING_BOTTOM_END,
    QUICK_PANEL_START, QUICK_PANEL_END, TAB_START, TAB_END,
}

@Serializable
enum class UiPresentation { BUTTON, MENU_ITEM }
@Serializable
enum class UiButtonStyle { ICON, ICON_TEXT }
@Serializable
enum class PluginIcon { INFO, MIC, SPEAKER, ADD, MENU, SETTINGS, TEXT, STAR, HEARING, CLOSE }

@Serializable
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
@Serializable
data class DrawerEntry(val id: String, val label: String, val pageId: String, val icon: PluginIcon,
    val settingsPage: Boolean = false)
@Serializable
enum class PluginGesture { QUICK_TEXT_LONG_PRESS }
@Serializable
data class GestureAction(val id: String, val label: String, val gesture: PluginGesture)
@Serializable
data class PluginContributions(
    val actions: List<UiAction> = emptyList(), val drawerEntries: List<DrawerEntry> = emptyList(),
    val gestures: List<GestureAction> = emptyList(),
)

@Serializable
data class PluginPage(val id: String, val title: String, val blocks: List<PageBlock>)
@Serializable
data class PageAction(val id: String, val label: String, val icon: PluginIcon = PluginIcon.INFO)

/** A plugin can build a new page without depending on Compose or an Activity. */
@Serializable
sealed interface PageBlock {
    @Serializable
    data class Text(val text: String, val heading: Boolean = false) : PageBlock
    @Serializable
    data class Notice(val text: String) : PageBlock
    @Serializable
    data class Actions(val actions: List<PageAction>) : PageBlock
    @Serializable
    data class Group(val title: String, val children: List<PageBlock>) : PageBlock
}
