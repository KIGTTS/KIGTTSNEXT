package com.kigtts.plugins.debug

import com.kigtts.plugin.api.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UiDebugPluginTest {
    private val context = PluginContext("", "")
    @Test fun defaultAddsNoDesignExternalButtons() {
        val registry = PluginRegistry(listOf(UiDebugPlugin()))
        assertTrue(UiSlot.entries.all { registry.actions(it).isEmpty() })
        assertEquals("config", registry.drawerEntries().single().entry.pageId)
    }
    @Test fun countsAndMenuPresentationRespectRequestedSlotAndPersist() = runTest {
        var saved: Map<UiSlot, Int> = emptyMap()
        val plugin = UiDebugPlugin(save = { saved = it })
        repeat(3) { assertEquals(PluginOutcome.RefreshUi, plugin.execute("EDITOR_MENU/add", context)) }
        repeat(2) { plugin.execute("HEADER_END/add", context) }
        val registry = PluginRegistry(listOf(plugin))
        assertEquals(3, registry.actions(UiSlot.EDITOR_MENU).size)
        assertTrue(registry.actions(UiSlot.EDITOR_MENU).all { it.action.presentation == UiPresentation.MENU_ITEM })
        assertEquals(2, registry.actions(UiSlot.HEADER_END).size)
        val restored = PluginRegistry(listOf(UiDebugPlugin(saved)))
        assertEquals(registry.actions(UiSlot.EDITOR_MENU).map { it.key }, restored.actions(UiSlot.EDITOR_MENU).map { it.key })
        plugin.execute("reset", context)
        assertTrue(UiSlot.entries.all { registry.actions(it).isEmpty() })
    }
    @Test fun countersAreBoundedAndEntrypointsOpenMenusAndPages() = runTest {
        val plugin = UiDebugPlugin()
        repeat(25) { plugin.execute("TAB_END/add", context) }
        assertEquals(20, plugin.contributions.actions.size)
        assertIs<PluginOutcome.Menu>(plugin.execute("TAB_END/0", context))
        assertEquals(PluginOutcome.OpenPage("config"), plugin.execute("config", context))
        assertNotNull(plugin.page("config", context))
        repeat(25) { plugin.execute("TAB_END/remove", context) }
        assertTrue(plugin.contributions.actions.isEmpty())
    }
    @Test fun buttonStyleIsConfiguredPerSlotAndRestored() = runTest {
        var saved: Map<UiSlot, UiButtonStyle> = emptyMap()
        val plugin = UiDebugPlugin(saveStyles = { saved = it })
        plugin.execute("EDITOR_TOP_END/add", context)
        plugin.execute("EDITOR_TOP_END/style", context)
        assertEquals(UiButtonStyle.ICON_TEXT, plugin.contributions.actions.single().buttonStyle)
        val restored = UiDebugPlugin(mapOf(UiSlot.EDITOR_TOP_END to 1), initialStyles = saved)
        assertEquals(UiButtonStyle.ICON_TEXT, restored.contributions.actions.single().buttonStyle)
    }
}
