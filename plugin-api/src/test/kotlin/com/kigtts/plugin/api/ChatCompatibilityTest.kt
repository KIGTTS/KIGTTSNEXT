package com.kigtts.plugin.api

import kotlin.test.*

class ChatCompatibilityTest {
    @Test fun apiNineChatConstructorsAndCopiesPreserveNewMetadata() {
        assertNotNull(ChatMessage::class.java.constructors.firstOrNull { it.parameterCount == 5 })
        assertNotNull(PageBlock.Chat::class.java.constructors.firstOrNull { it.parameterCount == 5 })
        assertNotNull(ChatMessage::class.java.declaredMethods.firstOrNull { it.name == "copy\$default" && it.parameterCount == 8 })
        assertNotNull(PageBlock.Chat::class.java.declaredMethods.firstOrNull { it.name == "copy\$default" && it.parameterCount == 8 })
        val message = ChatMessage("id", ChatRole.ASSISTANT, "one", summary = "summary", toolName = "file")
        assertEquals("summary", message.copy(text = "two").summary)
        val chat = PageBlock.Chat(listOf(message), "send", "stop", conversationId = "conversation", backgroundRunning = true)
        val updated = chat.copy(messages = emptyList())
        assertEquals("conversation", updated.conversationId); assertTrue(updated.backgroundRunning)
    }
    @Test fun currentHostAcceptsPreviousApiServices() {
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.legacy", "legacy", "test", apiVersion = 9)
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        assertEquals(plugin, PluginRegistry(listOf(plugin)).plugin("test.legacy"))
    }
}
