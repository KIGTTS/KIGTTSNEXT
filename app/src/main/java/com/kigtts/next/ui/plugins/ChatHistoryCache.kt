package com.kigtts.next.ui.plugins

import com.kigtts.plugin.api.*

/** UI pages are bounded Binder chunks. Previously loaded rows survive rotation in the host ViewModel. */
class ChatHistoryCache {
    private val expanded = mutableSetOf<String>()
    fun retainEarlier(key: String) { expanded += key }
    private val conversations = linkedMapOf<String, LinkedHashMap<String, ChatMessage>>()
    fun merge(key: String, chat: PageBlock.Chat): List<ChatMessage> {
        if (chat.historyAction == null) return chat.messages
        val messages = conversations.getOrPut(key) { linkedMapOf() }
        chat.messages.forEach { messages[it.id] = it }
        if (key !in expanded && messages.size > 100) {
            val retained = messages.values.sortedBy { it.position }.takeLast(100).map { it.id }.toSet()
            messages.keys.retainAll(retained)
        }
        if (conversations.size > 8) conversations.keys.firstOrNull { it != key }?.let { conversations.remove(it); expanded.remove(it) }
        return messages.values.sortedBy { it.position }
    }
}
