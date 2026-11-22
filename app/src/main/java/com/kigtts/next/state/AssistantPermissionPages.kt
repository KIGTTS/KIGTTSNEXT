package com.kigtts.next.state

import com.kigtts.plugin.api.PageAction
import com.kigtts.plugin.api.PageBlock
import com.kigtts.plugin.api.PluginIcon
import com.kigtts.plugin.api.PluginPage

internal const val ASSISTANT_SETTINGS_ID = "tools.cloud-agent"
internal const val ASSISTANT_PERMISSIONS_PAGE = "assistant-permissions"
internal const val ASSISTANT_PERMISSIONS_ACTION = "assistant-open-permissions"
internal const val ASSISTANT_SYSTEM_PERMISSIONS_ACTION = "assistant-open-app-permissions"

internal fun assistantSettingsPermissions(
    page: PluginPage,
    groupTitle: String,
    entryLabel: String,
): PluginPage {
    val blocks = page.blocks.mapNotNull { it.withoutToolPermission() }
    if (page.id != "settings") return page.copy(blocks = blocks)
    val action = PageAction(ASSISTANT_PERMISSIONS_ACTION, entryLabel, PluginIcon.NONE, symbol = "shield_locked")
    return page.copy(blocks = blocks + PageBlock.Group(groupTitle, listOf(PageBlock.Actions(listOf(action)))))
}

internal fun assistantPermissionsPage(settings: PluginPage, title: String, appPermissions: String): PluginPage {
    val controls = settings.blocks.flatMap { it.findToolPermissions() }
    val action = PageAction(ASSISTANT_SYSTEM_PERMISSIONS_ACTION, appPermissions, PluginIcon.NONE, symbol = "admin_panel_settings")
    return PluginPage(ASSISTANT_PERMISSIONS_PAGE, title,
        listOf(PageBlock.Group(title, controls + PageBlock.Actions(listOf(action)))))
}

private fun PageBlock.withoutToolPermission(): PageBlock? = when (this) {
    is PageBlock.Toggle -> takeUnless { actionId == "tools" }
    is PageBlock.Group -> children.mapNotNull { it.withoutToolPermission() }
        .takeIf { it.isNotEmpty() }?.let { copy(children = it) }
    else -> this
}

private fun PageBlock.findToolPermissions(): List<PageBlock.Toggle> = when (this) {
    is PageBlock.Toggle -> listOf(this).filter { actionId == "tools" }
    is PageBlock.Group -> children.flatMap { it.findToolPermissions() }
    else -> emptyList()
}
