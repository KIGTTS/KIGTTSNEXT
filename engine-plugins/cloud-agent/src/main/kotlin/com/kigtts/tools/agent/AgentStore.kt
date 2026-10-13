package com.kigtts.tools.agent

import com.kigtts.plugin.api.PluginJson
import com.kigtts.plugin.api.PluginStorage
import kotlinx.serialization.encodeToString
import java.io.File

/** Synchronizes UI reads with IO worker updates. Completed chunks are checkpointed, never replayed. */
class AgentStore(private val storage: PluginStorage) {
    private val settingsFile = storage.file("config/agent.json")
    private val conversationDirectory = storage.directory("conversations")
    @Volatile var settings: AgentSettings = read<AgentSettings>(settingsFile) ?: AgentSettings()
        private set
    @Volatile var pending: PendingReview? = read<PendingReview>(storage.file("config/pending-review.json"))
        private set
    private val conversations = linkedMapOf<String, Conversation>()
    init {
        conversationDirectory.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { file ->
            read<Conversation>(file)?.let { saved ->
                conversations[saved.id] = saved.copy(messages = saved.messages.map { message ->
                    if (message.state == MessageState.STREAMING) message.copy(state = MessageState.CANCELLED) else message
                })
            }
        }
    }
    @Synchronized fun updateSettings(change: (AgentSettings) -> AgentSettings) {
        settings = change(settings); write(settingsFile, settings)
    }
    @Synchronized fun list(): List<Conversation> = conversations.values.sortedByDescending { it.updatedAt }
    @Synchronized fun get(id: String): Conversation? = conversations[id]
    @Synchronized fun selected(): Conversation? = settings.selectedConversation?.let(conversations::get)
    @Synchronized fun create(): Conversation {
        check(conversations.size < 100) { "会话已达上限，请先删除不需要的会话" }
        val conversation = Conversation(); save(conversation)
        updateSettings { it.copy(selectedConversation = conversation.id) }; return conversation
    }
    @Synchronized fun select(id: String) { require(conversations.containsKey(id)); updateSettings { it.copy(selectedConversation = id) } }
    @Synchronized fun save(conversation: Conversation) {
        require(conversation.id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        require(conversation.messages.size <= 1000) { "此会话消息过多，请新建会话" }
        write(File(conversationDirectory, "${conversation.id}.json"), conversation)
        conversations[conversation.id] = conversation
    }
    @Synchronized fun message(id: String, message: AgentMessage, persist: Boolean = true) {
        val conversation = requireNotNull(conversations[id]); val items = conversation.messages.toMutableList()
        val index = items.indexOfFirst { it.id == message.id || (message.role == "tool" && message.toolCallId != null && it.toolCallId == message.toolCallId) }
        if (index < 0) items += message else items[index] = message.copy(id = items[index].id)
        val updated = conversation.copy(messages = items, updatedAt = System.currentTimeMillis())
        if (persist) save(updated) else conversations[id] = updated
    }
    @Synchronized fun delete(id: String) {
        conversations.remove(id); File(conversationDirectory, "$id.json").delete()
        if (settings.selectedConversation == id) updateSettings { it.copy(selectedConversation = list().firstOrNull()?.id) }
        if (pending?.conversationId == id) setPending(null)
    }
    @Synchronized fun setPending(value: PendingReview?) {
        pending = value
        val file = storage.file("config/pending-review.json")
        if (value == null) file.delete() else write(file, value)
    }
    private inline fun <reified T> read(file: File): T? = runCatching {
        if (file.length() > 16 * 1024 * 1024) null else PluginJson.decodeFromString<T>(file.readText(Charsets.UTF_8))
    }.getOrNull()
    private inline fun <reified T> write(file: File, value: T) {
        val json = PluginJson.encodeToString(value)
        require(json.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024) { "此会话已达保存上限，请新建会话" }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json, Charsets.UTF_8)
        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
