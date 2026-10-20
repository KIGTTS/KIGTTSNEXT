package com.kigtts.tools.agent

import com.kigtts.plugin.api.*

internal fun agentChat(store: AgentStore, runner: AgentRunner): PageBlock.Chat {
    val conversation = store.selected()
    val settings = conversation?.let(store::connection) ?: store.settings
    val profile = requireNotNull(settings.profiles[settings.provider])
    val pending = store.pending?.takeIf { it.conversationId == conversation?.id }
    val calls = conversation?.messages.orEmpty().flatMap { it.calls }.associateBy { it.id }
    val messages = visibleMessages(conversation?.messages.orEmpty()).mapNotNull { message ->
        if (message.role == "assistant" && message.calls.isNotEmpty() && message.content.isBlank()) return@mapNotNull null
        val tool = calls[message.toolCallId]
        val title = when (tool?.name) {
            "read_file" -> "已读取文件"; "write_file" -> "已写入文件"; "list_files" -> "已查看目录"
            "create_page" -> "创建网页卡片"; "web_search" -> "联网搜索"; "web_fetch" -> "读取网页"
            "deliver_lua" -> "安装 Lua 插件"; "draft_lua" -> "编写 Lua 插件"; "lua" -> "调试 Lua 插件"
            "quick_text" -> "管理快捷文本"; "stickers" -> "管理图片与贴纸"; else -> tool?.name ?: "软件操作"
        }
        val icon = when (tool?.name) { "read_file", "list_files" -> PluginIcon.TEXT; "web_search", "web_fetch" -> PluginIcon.INFO
            "create_page" -> PluginIcon.ADD; "write_file", "draft_lua" -> PluginIcon.EDIT; else -> PluginIcon.SETTINGS }
        ChatMessage(message.id, when (message.role) { "user" -> ChatRole.USER; "tool" -> ChatRole.TOOL; else -> ChatRole.ASSISTANT },
            if (message.role == "tool") toolSummary(message.content) else clippedMessage(message.content, 16 * 1024),
            reasoning = if (settings.showReasoning) message.reasoning.takeLast(8 * 1024) else "",
            state = ChatMessageState.valueOf(message.state.name), summary = reasoningSummary(message.reasoning), toolName = title,
            detail = if (message.role == "tool") message.content.take(2000) else "", icon = icon)
    }
    return PageBlock.Chat(messages, "send", "stop", runner.runningConversation == conversation?.id && runner.running,
        placeholder = "发送消息", conversationId = conversation?.id.orEmpty(), controls = listOf(
            ChatControl("模型", Provider.entries.map { provider ->
                ChatOption("chat-provider/${provider.name}", provider.label + " · " + (if (provider == settings.provider) profile.model else store.settings.profiles[provider]?.model.orEmpty()))
            } + ChatOption("chat-model", "输入模型名称…"), "chat-provider/${settings.provider.name}"),
            ChatControl("权限", listOf(ChatOption("access/ask", "请求批准", "ask"), ChatOption("access/full", "完全访问", "full")),
                if (settings.access == AgentAccessMode.FULL) "access/full" else "access/ask", ChatControlKind.ACCESS)),
        approval = pending?.let { ChatApproval("请求批准", "resume", "discard-review", it.summary.take(4000),
            if (it.token.isNotEmpty()) "review" else null) }, backgroundRunning = runner.running)
}
internal fun reasoningSummary(reasoning: String): String {
    if (reasoning.isBlank()) return ""
    val sentences = reasoning.replace(Regex("\\s+"), " ").split(Regex("(?<=[。！？.!?])\\s*"))
        .map(String::trim).filter(String::isNotBlank)
    val summary = if (sentences.size > 1) sentences.first().take(160) + " … " + sentences.last().takeLast(160) else sentences.firstOrNull().orEmpty().take(320)
    return "思考摘要：$summary"
}
