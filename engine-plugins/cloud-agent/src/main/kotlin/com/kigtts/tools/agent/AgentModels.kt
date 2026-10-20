package com.kigtts.tools.agent

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable enum class Provider(val label: String, val endpoint: String, val model: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    GLM("GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3"),
    KIMI("Kimi", "https://api.moonshot.ai/v1", "kimi-k3"),
    CUSTOM("自定义", "", ""),
}
@Serializable data class ProviderProfile(val endpoint: String, val model: String)
@Serializable data class AgentSettings(val provider: Provider = Provider.DEEPSEEK,
    val profiles: Map<Provider, ProviderProfile> = Provider.entries.associateWith { ProviderProfile(it.endpoint, it.model) },
    val showReasoning: Boolean = false, val access: AgentAccessMode = AgentAccessMode.REQUEST_APPROVAL, val toolsEnabled: Boolean = true, val selectedConversation: String? = null)
@Serializable enum class AgentAccessMode { REQUEST_APPROVAL, FULL }
@Serializable data class ToolCall(val id: String, val name: String, val arguments: String)
@Serializable enum class MessageState { COMPLETE, STREAMING, FAILED, CANCELLED }
@Serializable data class AgentMessage(val id: String = UUID.randomUUID().toString(), val role: String,
    val content: String = "", val reasoning: String = "", val calls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null, val state: MessageState = MessageState.COMPLETE)
@Serializable data class Conversation(val id: String = UUID.randomUUID().toString(), val title: String = "新会话",
    val messages: List<AgentMessage> = emptyList(), val provider: Provider? = null, val model: String? = null,
    val updatedAt: Long = System.currentTimeMillis())
@Serializable enum class ReviewKind { QUICK_TEXT, STICKERS, EXTERNAL, TOOL }
@Serializable data class PendingReview(val conversationId: String, val toolCallId: String, val token: String,
    val summary: String, val createdAt: Long = System.currentTimeMillis(), val kind: ReviewKind = ReviewKind.QUICK_TEXT,
    val transactionId: String? = null, val call: ToolCall? = null)

internal fun completionEndpoint(base: String): String {
    val uri = URI(base.trim())
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.query == null) {
        "服务地址需要使用 HTTPS，且不能包含帐号或查询参数"
    }
    return uri.toASCIIString().trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }
}

internal fun AgentStore.connection(conversation: Conversation): AgentSettings {
    val provider = conversation.provider ?: settings.provider
    val profile = requireNotNull(settings.profiles[provider])
    return settings.copy(provider = provider, profiles = settings.profiles + (provider to profile.copy(model = conversation.model ?: profile.model)))
}
