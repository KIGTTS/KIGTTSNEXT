package com.kigtts.plugin.api

import kotlin.test.*

class PluginRegistryTest {
    private fun plugin(id: String, api: Int = 1, actions: List<UiAction> = emptyList()) = object : KigPlugin {
        override val descriptor = PluginDescriptor(id, id, "test", apiVersion = api)
        override val contributions = PluginContributions(actions)
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.Message(actionId)
    }
    @Test fun rejectsDuplicateAndIncompatiblePlugins() {
        assertFailsWith<IllegalArgumentException> { PluginRegistry(listOf(plugin("test.a"), plugin("test.a"))) }
        assertFailsWith<IllegalArgumentException> { PluginRegistry(listOf(plugin("test.a", api = 2))) }
    }
    @Test fun actionsRemainNamespacedAndDeterministicallyOrdered() {
        val registry = PluginRegistry(listOf(
            plugin("test.b", actions = listOf(UiAction("open", "B", UiSlot.TAB_END, order = 5))),
            plugin("test.a", actions = listOf(UiAction("open", "A", UiSlot.TAB_END, order = 1))),
        ))
        assertEquals(listOf("test.a/open", "test.b/open"), registry.actions(UiSlot.TAB_END).map { it.key })
        assertTrue(registry.actions(UiSlot.TAB_START).isEmpty())
    }
    @Test fun rejectsIncorrectMenuPlacementAndDuplicateActions() {
        assertFailsWith<IllegalArgumentException> { PluginRegistry(listOf(plugin("test.a", actions = listOf(UiAction("menu", "Menu", UiSlot.EDITOR_MENU))))) }
        val action = UiAction("open", "Open", UiSlot.HEADER_START)
        assertFailsWith<IllegalArgumentException> { PluginRegistry(listOf(plugin("test.a", actions = listOf(action, action)))) }
    }
    @Test fun validatesPcmFrameAlignment() {
        assertFailsWith<IllegalArgumentException> { PcmAudio(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { PcmAudio(ByteArray(2), channels = 2) }
        assertFailsWith<IllegalArgumentException> { PcmAudio(ByteArray(4), sampleRate = 0) }
    }
}
