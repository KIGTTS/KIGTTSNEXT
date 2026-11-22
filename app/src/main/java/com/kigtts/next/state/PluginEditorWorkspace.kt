package com.kigtts.next.state

import com.kigtts.plugin.api.ManagementItem
import com.kigtts.plugin.api.PageBlock
import com.kigtts.plugin.api.PluginPage

internal const val PLUGIN_EDITOR_ID = "runtime.lua"
internal const val NATIVE_BUILDER_ID = "tools.native-builder"
internal const val EDITOR_MANAGER_PAGE = "manager"
private const val NATIVE_ACTION_PREFIX = "native-editor/"

internal fun editorWorkspace(editor: PluginPage, native: PluginPage?): PluginPage {
    if (native == null) return editor
    val list = editor.management ?: return editor
    val nativeItems = native.management?.items.orEmpty().map { row ->
        ManagementItem(id = "native/${row.id}", title = row.title, subtitle = row.subtitle,
            icon = row.icon, symbol = row.symbol, badges = row.badges,
            primaryAction = row.primaryAction?.let { NATIVE_ACTION_PREFIX + it })
    }
    val buildTools = native.blocks.drop(1).mapNotNull { block ->
        if (block !is PageBlock.Group) return@mapNotNull block.takeIf { it is PageBlock.Notice }
        block.copy(children = block.children.map { child ->
            if (child is PageBlock.Actions) child.copy(actions = child.actions.map { it.copy(id = NATIVE_ACTION_PREFIX + it.id) })
            else child
        })
    }
    return editor.copy(blocks = editor.blocks + buildTools, management = list.copy(items = list.items + nativeItems))
}

internal fun editorNativeAction(pluginId: String, pageId: String, action: String): String? =
    action.removePrefix(NATIVE_ACTION_PREFIX).takeIf {
        pluginId == PLUGIN_EDITOR_ID && pageId == EDITOR_MANAGER_PAGE && action.startsWith(NATIVE_ACTION_PREFIX)
    }
