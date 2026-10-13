package com.kigtts.tools.agent

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import java.util.UUID

class CloudAgentPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("tools.cloud-agent", "云端助手", "对话、管理快捷文本与创建小工具", version = "0.8.1")
    private lateinit var environment: PluginEnvironment
    private lateinit var store: AgentStore
    private lateinit var secrets: AgentSecrets
    private lateinit var runner: AgentRunner
    private lateinit var reviews: AgentReviewController
    private val revision = MutableStateFlow(0L)
    override val uiChanges = revision.asStateFlow()
    private fun changed() { revision.update { it + 1 } }
    override suspend fun attach(environment: PluginEnvironment) {
        this.environment = environment
        store = AgentStore(environment.storage)
        secrets = KeystoreAgentSecrets(environment.storage)
        runner = AgentRunner(store, secrets, HttpsChatTransport(), AgentTools(environment), ::changed)
        reviews = AgentReviewController(environment, store, runner)
    }
    override val contributions = PluginContributions(settingsEntries = listOf(
        SettingsEntry("assistant", "AI 助手", "chat", PluginIcon.STAR, cardId = "assistant", cardTitle = "助手", summary = "对话与快捷文本管理"),
        SettingsEntry("settings", "连接与对话", "settings", PluginIcon.SETTINGS, cardId = "assistant", cardTitle = "助手", summary = "服务、模型与密钥", order = 1)),
        automationActions = listOf("open"))
    override fun page(pageId: String, context: PluginContext) = agentPage(pageId, store, runner, secrets)
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        val value = context.inputs["value"].orEmpty()
        try {
            val result = when {
                actionId == "open" -> PluginOutcome.OpenPage("chat")
                actionId == "settings" || actionId == "conversations" -> PluginOutcome.OpenPage(actionId)
                actionId == "new" -> {
                    check(!runner.running) { "请先停止当前回答" }; store.create(); PluginOutcome.OpenPage("chat")
                }
                actionId.startsWith("select/") -> { store.select(actionId.removePrefix("select/")); PluginOutcome.OpenPage("chat") }
                actionId.startsWith("rename/") -> {
                    val id = actionId.removePrefix("rename/")
                    PluginOutcome.TextInput("会话名称", "rename-save/$id", requireNotNull(store.get(id)).title)
                }
                actionId.startsWith("rename-save/") -> {
                    require(value.trim().length in 1..80)
                    val conversation = requireNotNull(store.get(actionId.removePrefix("rename-save/")))
                    store.save(conversation.copy(title = value.trim())); PluginOutcome.RefreshUi
                }
                actionId.startsWith("delete/") -> PluginOutcome.Menu("删除会话？", listOf(
                    PageAction("delete-confirm/${actionId.removePrefix("delete/")}", "删除", PluginIcon.CLOSE)))
                actionId.startsWith("delete-confirm/") -> {
                    val id = actionId.removePrefix("delete-confirm/")
                    check(runner.runningConversation != id) { "请先停止此会话的回答" }
                    store.delete(id); PluginOutcome.RefreshUi
                }
                actionId == "send" -> {
                    val text = context.inputs["message"] ?: context.inputs["text"] ?: value
                    val conversation = store.selected() ?: store.create()
                    runner.send(conversation.id, text); PluginOutcome.RefreshUi
                }
                actionId == "stop" -> { runner.stop(); PluginOutcome.RefreshUi }
                actionId == "export-conversation" -> {
                    val conversation = store.selected() ?: return PluginOutcome.Message("还没有会话")
                    val path = "exports/conversation-${conversation.id}.json"
                    val file = environment.storage.file(path); file.parentFile?.mkdirs()
                    file.writeText(PluginJson.encodeToString(conversation), Charsets.UTF_8)
                    PluginOutcome.ExportResource("导出会话", PluginResource(path), "助手会话.json", mimeType = "application/json")
                }
                actionId.startsWith("provider/") -> {
                    check(!runner.running) { "请先停止当前回答" }
                    val provider = Provider.valueOf(actionId.removePrefix("provider/"))
                    store.updateSettings { it.copy(provider = provider) }; PluginOutcome.RefreshUi
                }
                actionId in listOf("endpoint", "model", "key") -> {
                    val profile = requireNotNull(store.settings.profiles[store.settings.provider])
                    PluginOutcome.TextInput(when (actionId) { "endpoint" -> "服务地址"; "model" -> "模型"; else -> "密钥" },
                        "save-$actionId", when (actionId) { "endpoint" -> profile.endpoint; "model" -> profile.model; else -> "" }, secret = actionId == "key")
                }
                actionId.startsWith("save-") -> {
                    check(!runner.running) { "请先停止当前回答" }
                    val field = actionId.removePrefix("save-"); val provider = store.settings.provider
                    if (field == "key") secrets.write(provider, value)
                    else {
                        val text = value.trim(); require(text.length <= 512)
                        if (field == "endpoint") completionEndpoint(text) else require(text.isNotBlank())
                        store.updateSettings { settings ->
                            val old = requireNotNull(settings.profiles[provider])
                            settings.copy(profiles = settings.profiles + (provider to if (field == "endpoint") old.copy(endpoint = text) else old.copy(model = text)))
                        }
                    }
                    PluginOutcome.RefreshUi
                }
                actionId == "reasoning" -> { store.updateSettings { it.copy(showReasoning = !it.showReasoning) }; PluginOutcome.RefreshUi }
                actionId == "tools" -> {
                    check(!runner.running) { "请先停止当前回答" }; store.updateSettings { it.copy(toolsEnabled = !it.toolsEnabled) }; PluginOutcome.RefreshUi
                }
                actionId == "review" -> reviews.open()
                actionId == "resume" -> reviews.resume()
                actionId == "discard-review" -> reviews.discard()
                actionId == "import-cards" -> PluginOutcome.ImportResource("导入快捷文本", "resources/quick-imports", "import-cards-complete")
                actionId == "import-cards-complete" -> {
                    val broker = requireNotNull(environment.quickTextBroker)
                    val revision = (broker.request(QuickTextCommand.Query(limit = 0)) as QuickTextReply.Snapshot).value.revision
                    val source = PluginResource(requireNotNull(context.inputs["resourcePath"]))
                    prepared(broker.request(QuickTextCommand.Import(source, UUID.randomUUID().toString(), revision)))
                }
                actionId == "export-cards" -> when (val reply = requireNotNull(environment.quickTextBroker).request(QuickTextCommand.Export())) {
                    is QuickTextReply.Resource -> PluginOutcome.ExportResource("导出快捷文本", reply.source, "快捷文本.kigcards")
                    else -> PluginOutcome.Message((reply as? QuickTextReply.Failure)?.message ?: "导出未完成")
                }
                else -> PluginOutcome.Message("未知助手操作")
            }
            changed(); return result
        } catch (error: IllegalArgumentException) { return PluginOutcome.Message(error.message?.take(200) ?: "请检查输入内容") }
        catch (error: IllegalStateException) { return PluginOutcome.Message(error.message?.take(200) ?: "操作暂时无法完成") }
        catch (_: java.io.IOException) { return PluginOutcome.Message("文件暂时无法保存，请稍后重试") }
    }
    private suspend fun prepared(reply: QuickTextReply): PluginOutcome {
        if (reply !is QuickTextReply.Prepared) return PluginOutcome.Message((reply as? QuickTextReply.Failure)?.message ?: "导入未完成")
        val conversation = store.selected() ?: store.create()
        store.setPending(PendingReview(conversation.id, "", reply.value.token, "导入内容已准备好，请审核"))
        return reviews.open()
    }
    override suspend fun close() { if (::runner.isInitialized) runner.close() }
}
