package com.kigtts.tools.agent

import com.kigtts.plugin.api.*

internal fun agentPage(id: String, store: AgentStore, runner: AgentRunner, secrets: AgentSecrets): PluginPage? {
    val settings = store.settings
    return when (id) {
        "chat" -> {
            val conversation = store.selected()
            val blocks = mutableListOf<PageBlock>(PageBlock.Actions(listOf(
                PageAction("new", "新会话", PluginIcon.ADD), PageAction("conversations", "会话", PluginIcon.TEXT),
                PageAction("settings", "设置", PluginIcon.SETTINGS), PageAction("export-conversation", "导出会话", PluginIcon.EXPORT))))
            store.pending?.takeIf { it.conversationId == conversation?.id }?.let { pending ->
                blocks += PageBlock.Group("待审核", listOf(PageBlock.Notice(pending.summary), PageBlock.Actions(buildList {
                    if (pending.token.isNotEmpty()) add(PageAction("review", "审核变更", PluginIcon.EDIT))
                    add(PageAction("resume", "继续", PluginIcon.PIN)); add(PageAction("discard-review", "取消操作", PluginIcon.CLOSE))
                })))
            }
            blocks += PageBlock.Chat(visibleMessages(conversation?.messages.orEmpty()).map { message ->
                ChatMessage(message.id, when (message.role) { "user" -> ChatRole.USER; "tool" -> ChatRole.TOOL; else -> ChatRole.ASSISTANT },
                    if (message.role == "tool") toolSummary(message.content) else clippedMessage(message.content, 16 * 1024),
                    reasoning = if (settings.showReasoning) message.reasoning.takeLast(8 * 1024) else "",
                    state = ChatMessageState.valueOf(message.state.name))
            }, sendAction = "send", stopAction = "stop", running = runner.running,
                placeholder = if (conversation == null) "开始一段新对话" else "发送消息")
            PluginPage(id, conversation?.title ?: "云端助手", blocks)
        }
        "conversations" -> PluginPage(id, "会话", listOf(PageBlock.Actions(listOf(PageAction("new", "新会话", PluginIcon.ADD)))),
            management = ManagementList("conversations", store.list().map { conversation ->
                ManagementItem(conversation.id, conversation.title,
                    subtitle = "${conversation.messages.count { it.role == "user" }} 条消息", icon = PluginIcon.TEXT,
                    selected = settings.selectedConversation == conversation.id, primaryAction = "select/${conversation.id}",
                    actions = listOf(PageAction("rename/${conversation.id}", "重命名", PluginIcon.EDIT), PageAction("delete/${conversation.id}", "删除", PluginIcon.CLOSE)))
            }, emptyText = "创建会话，开始聊天"))
        "settings" -> {
            val profile = requireNotNull(settings.profiles[settings.provider])
            PluginPage(id, "助手设置", listOf(
                PageBlock.Group("连接", listOf(
                    PageBlock.Choice("服务", Provider.entries.map { PageAction("provider/${it.name}", it.label) }, "provider/${settings.provider.name}"),
                    PageBlock.Input("服务地址", profile.endpoint, "endpoint"),
                    PageBlock.Input("模型", profile.model, "model"),
                    PageBlock.Input("密钥", if (secrets.exists(settings.provider)) "已设置" else "未设置", "key", secret = true))),
                PageBlock.Group("对话", listOf(
                    PageBlock.Toggle("显示思考过程", settings.showReasoning, "reasoning", "显示服务返回的思考内容", PluginIcon.TEXT),
                    PageBlock.Toggle("允许操作软件", settings.toolsEnabled, "tools", "管理快捷文本、贴纸与小工具", PluginIcon.SETTINGS))),
                PageBlock.Group("快捷文本", listOf(PageBlock.Actions(listOf(
                    PageAction("import-cards", "导入", PluginIcon.ADD), PageAction("export-cards", "导出", PluginIcon.EXPORT)))))
            ))
        }
        else -> null
    }
}
private fun clippedMessage(text: String, limit: Int) = if (text.length <= limit) text else "较长内容仅显示结尾，可导出完整会话\n\n" + text.takeLast(limit)
/** Snapshots include several pages and must stay below the SDK's 512 KiB Binder budget. */
private fun visibleMessages(messages: List<AgentMessage>): List<AgentMessage> {
    var budget = 48 * 1024
    return messages.takeLast(80).asReversed().takeWhile { message ->
        budget -= message.content.takeLast(16 * 1024).length + message.reasoning.takeLast(8 * 1024).length + 512
        budget >= 0
    }.asReversed()
}
private fun toolSummary(content: String): String = when {
    content.contains("CONFIRMATION_REQUIRED") || content.contains("\"pending\":true") || content.contains("\"requiresConfirmation\":true") -> "等待你审核操作"
    content.contains("\"type\":\"prepared\"") -> "变更已准备"
    content.contains("discarded_after_user_review") || content.contains("\"type\":\"discarded\"") -> "操作已取消"
    content.contains("OUTCOME_UNKNOWN") || content.contains("outcome_unknown") -> "操作状态待确认"
    content.contains("\"error\"") || content.contains("\"type\":\"failure\"") -> "操作未完成"
    content.contains("not_executed_pending_review") -> "后续操作已暂缓"
    else -> "已完成操作"
}
