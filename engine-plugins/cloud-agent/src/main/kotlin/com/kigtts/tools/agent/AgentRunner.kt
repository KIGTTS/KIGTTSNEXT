package com.kigtts.tools.agent

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One active response, independent conversations, no implicit retries of network or host mutations. */
class AgentRunner(private val store: AgentStore, private val secrets: AgentSecrets, private val transport: ChatTransport,
    private val tools: AgentTools, private val changed: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    @Volatile private var active: Job? = null
    @Volatile var runningConversation: String? = null; private set
    // A cancelled worker still owns its connection until its finally block completes.
    val running get() = active != null
    @Synchronized fun send(conversationId: String, text: String) {
        check(!running) { "请先停止当前回答" }; check(store.pending == null) { "请先完成或取消待审核操作" }
        require(text.isNotBlank() && text.length <= 32 * 1024)
        val settings = store.connection(requireNotNull(store.get(conversationId)))
        val profile = requireNotNull(settings.profiles[settings.provider]); completionEndpoint(profile.endpoint)
        check(profile.model.isNotBlank()) { "请在连接设置中填写模型" }
        check(secrets.exists(settings.provider)) { "请先在连接设置中填写密钥" }
        val conversation = requireNotNull(store.get(conversationId))
        if (conversation.messages.isEmpty()) store.save(conversation.copy(title = text.trim().take(36)))
        store.message(conversationId, AgentMessage(role = "user", content = text.trim()))
        launch(conversationId, settings)
    }
    @Synchronized fun continueAfterReview(conversationId: String) {
        check(!running)
        store.message(conversationId, AgentMessage(role = "user", content = "我已处理待确认事项，请先查询当前状态，然后继续。不要重复已经完成的操作。"))
        launch(conversationId, store.connection(requireNotNull(store.get(conversationId))))
    }
    @Synchronized fun approve(pending: PendingReview) {
        check(!running)
        check(store.settings.toolsEnabled) { "请先开启允许操作软件" }
        val call = requireNotNull(pending.call)
        store.setPending(pending.copy(kind = ReviewKind.EXTERNAL, call = null, summary = "操作可能已经执行，请先检查状态后继续"))
        launch(pending.conversationId, store.connection(requireNotNull(store.get(pending.conversationId))), call)
    }
    private fun launch(id: String, settings: AgentSettings, approved: ToolCall? = null) {
        runningConversation = id
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (approved != null) {
                    val reply = try { tools.execute(approved) } catch (error: Exception) {
                        if (error is CancellationException) {
                            store.message(id, AgentMessage(role = "tool", toolCallId = approved.id, content = "{\"status\":\"cancelled_outcome_unknown\"}"))
                            throw error
                        }
                        ToolResult("{\"error\":\"OUTCOME_UNKNOWN\"}", pause = true, summary = "操作状态尚不明确，请检查后继续", reviewKind = ReviewKind.EXTERNAL)
                    }
                    store.message(id, AgentMessage(role = "tool", content = reply.content, toolCallId = approved.id))
                    if (reply.pause) {
                        store.setPending(PendingReview(id, approved.id, reply.reviewToken.orEmpty(), reply.summary ?: "操作需要进一步确认", kind = reply.reviewKind))
                        return@launch
                    }
                    store.setPending(null)
                }
                respond(id, settings)
            }
            finally { runningConversation = null; active = null; changed() }
        }
        active = job
        job.start()
        changed()
    }
    private suspend fun respond(id: String, settings: AgentSettings) {
        try {
            val key = secrets.read(settings.provider)
            val endpoint = completionEndpoint(requireNotNull(settings.profiles[settings.provider]).endpoint)
            repeat(8) {
                currentCoroutineContext().ensureActive()
                var message = AgentMessage(role = "assistant", state = MessageState.STREAMING)
                store.message(id, message); changed()
                try {
                    var checkpointAt = 0L; var savedAt = 0L
                    val result = transport.stream(endpoint, key, chatRequest(settings, requireNotNull(store.get(id)), tools.schemas)) { partial ->
                        // Persist and notify at most 12.5 times/s; final state is always saved below.
                        val now = System.nanoTime()
                        message = message.copy(content = partial.content, reasoning = partial.reasoningContent)
                        if (now - checkpointAt > 80_000_000L || partial.ended || partial.finishReason != null) {
                            checkpointAt = now
                            val persist = now - savedAt > 500_000_000L || partial.ended || partial.finishReason != null
                            if (persist) savedAt = now
                            store.message(id, message, persist); changed()
                        }
                    }
                    message = message.copy(content = result.content, reasoning = result.reasoningContent,
                        calls = result.result(), state = MessageState.COMPLETE)
                    store.message(id, message); changed()
                    if (message.calls.isEmpty()) return
                    if (!settings.toolsEnabled) {
                        message.calls.forEach { call -> store.message(id, AgentMessage(role = "tool", toolCallId = call.id,
                            content = "{\"error\":\"OPERATIONS_DISABLED\",\"message\":\"用户关闭了操作软件\"}")) }
                        changed(); return
                    }
                    for ((index, call) in message.calls.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        if (store.settings.access != AgentAccessMode.FULL && tools.requiresApproval(call)) {
                            store.setPending(PendingReview(id, call.id, "", tools.describe(call), kind = ReviewKind.TOOL, call = call))
                            message.calls.drop(index).forEach { deferred -> store.message(id, AgentMessage(role = "tool",
                                content = "{\"status\":\"not_executed_pending_review\"}", toolCallId = deferred.id)) }
                            changed(); return
                        }
                        val reply = try { tools.execute(call) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            ToolResult("{\"error\":\"OUTCOME_UNKNOWN\",\"message\":\"不要重试修改操作，请先查询状态\"}",
                                pause = true, summary = "操作状态尚不明确，请检查后继续", reviewKind = ReviewKind.EXTERNAL)
                        }
                        store.message(id, AgentMessage(role = "tool", content = reply.content, toolCallId = call.id)); changed()
                        if (reply.pause) {
                            val summary = reply.summary ?: if (reply.reviewToken != null) "变更需要你的审核" else "草稿已送交审核，完成后可以继续"
                            store.setPending(PendingReview(id, call.id, reply.reviewToken.orEmpty(), summary,
                                kind = reply.reviewKind, transactionId = reply.transactionId))
                            // Complete every call/result pair without running subsequent mutations after review is requested.
                            message.calls.drop(index + 1).forEach { deferred ->
                                store.message(id, AgentMessage(role = "tool", content = "{\"status\":\"not_executed_pending_review\"}", toolCallId = deferred.id))
                            }
                            changed(); return
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (message.state == MessageState.STREAMING) store.message(id, message.copy(state = MessageState.CANCELLED))
                    else completeUnansweredCalls(id, message)
                    changed(); throw cancelled
                } catch (error: Exception) {
                    if (!currentCoroutineContext().isActive) {
                        if (message.state == MessageState.STREAMING) store.message(id, message.copy(state = MessageState.CANCELLED))
                        else completeUnansweredCalls(id, message)
                        changed(); return
                    }
                    store.message(id, message.copy(content = message.content.ifBlank { error.message?.take(240) ?: "回答中断，请稍后重试" }, state = MessageState.FAILED)); changed(); return
                }
            }
            store.message(id, AgentMessage(role = "assistant", content = "本次操作步骤较多，已暂停。你可以继续发送消息。")); changed()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            store.message(id, AgentMessage(role = "assistant", content = error.message?.take(240) ?: "暂时无法连接", state = MessageState.FAILED)); changed()
        }
    }
    private fun completeUnansweredCalls(id: String, message: AgentMessage) {
        val answered = store.get(id)?.messages.orEmpty().mapNotNull { it.toolCallId }.toSet()
        message.calls.filter { it.id !in answered }.forEach { call ->
            store.message(id, AgentMessage(role = "tool", toolCallId = call.id,
                content = "{\"status\":\"cancelled_outcome_unknown\",\"message\":\"不要重试修改操作，请先查询状态\"}"))
        }
    }
    fun stop() { active?.cancel(); transport.cancel(); changed() }
    suspend fun close() { stop(); active?.join(); scope.cancel() }
}
