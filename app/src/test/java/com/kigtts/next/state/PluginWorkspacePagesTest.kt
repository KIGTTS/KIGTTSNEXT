package com.kigtts.next.state

import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class PluginWorkspacePagesTest {
    @Test fun editorSharesOneListWithTheNativeBackendAndScopesItsActions() {
        val lua = PluginPage("manager", "Editor", listOf(PageBlock.Group("Projects",
            listOf(PageBlock.Actions(listOf(PageAction("create", "New")))))),
            ManagementList("projects", listOf(ManagementItem("same", "Lua project", primaryAction = "project/same"))))
        val native = PluginPage("manager", "Builder", listOf(
            PageBlock.Group("Duplicate project controls", listOf(PageBlock.Actions(listOf(PageAction("new", "New"))))),
            PageBlock.Group("Tools", listOf(PageBlock.Actions(listOf(PageAction("choose-toolchain", "Import tools")))))),
            ManagementList("native", listOf(ManagementItem("same", "Java project", primaryAction = "project/same"))))
        val combined = editorWorkspace(lua, native)
        val items = requireNotNull(combined.management).items
        assertEquals(listOf("same", "native/same"), items.map { it.id })
        assertEquals("project/same", editorNativeAction(PLUGIN_EDITOR_ID, "manager", requireNotNull(items.last().primaryAction)))
        assertNull(editorNativeAction(PLUGIN_EDITOR_ID, "project/same", requireNotNull(items.last().primaryAction)))
        assertEquals(listOf("Projects", "Tools"), combined.blocks.filterIsInstance<PageBlock.Group>().map { it.title })
    }
}
