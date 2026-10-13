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
enum class PluginIcon { INFO, MIC, SPEAKER, ADD, MENU, SETTINGS, TEXT, STAR, HEARING, CLOSE, PIN, EDIT, IMAGE, EXPORT, NONE }

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
/** A feature entry shares the native settings card. Reserved card IDs have host-owned titles and order. */
@Serializable
data class SettingsEntry(val id: String, val label: String, val pageId: String,
    val icon: PluginIcon = PluginIcon.SETTINGS, val cardId: String = "extensions",
    val cardTitle: String = "功能与扩展", val summary: String = "", val order: Int = 0)
/** Source runtimes expose actual installed/approved projects to dependency resolution. Drafts are excluded. */
@Serializable
data class ManagedPackage(val id: String, val version: String, val kind: String = "lua", val enabled: Boolean = true)
@Serializable
enum class PluginGesture { QUICK_TEXT_LONG_PRESS }
@Serializable
data class GestureAction(val id: String, val label: String, val gesture: PluginGesture)
@Serializable
data class PluginContributions(
    val actions: List<UiAction> = emptyList(), val drawerEntries: List<DrawerEntry> = emptyList(),
    val gestures: List<GestureAction> = emptyList(),
    /** Only these explicit entry points can be called through AppBroker by another plugin. Review buttons stay private. */
    val automationActions: List<String> = emptyList(),
    val settingsEntries: List<SettingsEntry> = emptyList(),
    val managedPackages: List<ManagedPackage> = emptyList(),
    /** Source deep links are delivered as untrusted inputs["url"], never fetched or installed by the host. */
    val sourceAction: String? = null,
)

@Serializable
data class PluginPage(val id: String, val title: String, val blocks: List<PageBlock>, val management: ManagementList? = null)
@Serializable
data class PageAction(val id: String, val label: String, val icon: PluginIcon = PluginIcon.INFO)

/** A plugin can build a new page without depending on Compose or an Activity. */
@Serializable
sealed interface PageBlock {
    @Serializable
    data class SourceEditor(val id: String, val text: String, val saveAction: String,
        val language: String = "lua", val readOnly: Boolean = false) : PageBlock
    @Serializable
    data class Files(val title: String, val items: List<PluginFileItem>, val directory: String,
        val openAction: String, val operationAction: String, val importAction: String? = null,
        val createAction: String? = null, val parentAction: String? = null) : PageBlock
    @Serializable
    data class Toggle(val title: String, val checked: Boolean, val actionId: String,
        val summary: String = "", val icon: PluginIcon = PluginIcon.SETTINGS) : PageBlock
    @Serializable
    data class Choice(val title: String, val options: List<PageAction>, val selected: String,
        val icon: PluginIcon = PluginIcon.SETTINGS) : PageBlock
    @Serializable
    data class Input(val title: String, val value: String, val actionId: String,
        val secret: Boolean = false, val multiline: Boolean = false, val icon: PluginIcon = PluginIcon.EDIT) : PageBlock
    @Serializable
    data class Chat(val messages: List<ChatMessage>, val sendAction: String, val stopAction: String,
        val running: Boolean = false, val placeholder: String = "发送消息") : PageBlock
    @Serializable
    data class Text(val text: String, val heading: Boolean = false) : PageBlock
    @Serializable
    data class Notice(val text: String) : PageBlock
    @Serializable
    data class Actions(val actions: List<PageAction>) : PageBlock
    @Serializable
    data class Group(val title: String, val children: List<PageBlock>) : PageBlock
}
