// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.android.AndroidPluginEnvironment
import com.kigtts.plugin.api.*
import com.kigtts.engines.system.SystemSpeechEngine
import java.util.Base64

class SpeechServicePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("service.speech-synthesis", "语音合成服务",
        "统一管理 TTS 引擎与语音包，内置系统 TTS 保底", version = "0.5.0", license = "GPL-3.0-only")
    private lateinit var environment: AndroidPluginEnvironment
    private lateinit var settings: ServiceSettings
    private lateinit var broker: SpeechBroker
    private lateinit var facade: TtsEngine
    private lateinit var system: SystemSpeechEngine
    private lateinit var libraryPages: VoiceLibraryPages
    private lateinit var libraryActions: VoiceLibraryActions
    override val tts: TtsEngine get() = facade
    override suspend fun attach(environment: PluginEnvironment) {
        this.environment = environment as AndroidPluginEnvironment
        broker = requireNotNull(environment.speechBroker) { "宿主未授予语音合成服务能力" }
        settings = ServiceSettings(environment.storage)
        system = SystemSpeechEngine(this.environment) { settings.language to 1f }
        facade = ServiceSynthesis(settings, broker, system)
        val library = VoiceLibrary(environment.storage)
        libraryPages = VoiceLibraryPages(settings, library, ::providers)
        libraryActions = VoiceLibraryActions(environment, settings, library, libraryPages, broker)
    }
    override val contributions = PluginContributions(drawerEntries = listOf(
        DrawerEntry("manager", "语音合成服务", "manager", PluginIcon.SPEAKER),
        DrawerEntry("packs", "语音包管理", "packs", PluginIcon.SPEAKER)))
    override fun page(pageId: String, context: PluginContext): PluginPage? {
        libraryPages.page(pageId)?.let { return it }
        if (pageId == "manager") {
            val providers = providers()
            val active = providers.firstOrNull { it.pluginId == settings.provider }
            return PluginPage(pageId, "语音合成服务", listOf(
                PageBlock.Text("语音合成服务", true),
                PageBlock.Notice("当前引擎：${active?.name ?: "内置系统 TTS"} · 外部引擎不可用时自动使用系统 TTS"),
                PageBlock.Actions(listOf(PageAction("activate", "设为应用合成服务", PluginIcon.SPEAKER),
                    PageAction("library", "管理所有语音包", PluginIcon.SETTINGS),
                    PageAction("system", "使用内置系统 TTS", PluginIcon.SPEAKER), PageAction("rate", "语速：${settings.rate}", PluginIcon.SETTINGS),
                    PageAction("language", "系统 TTS 语言：${settings.language}", PluginIcon.SETTINGS))),
                PageBlock.Group("引擎与声音", if (providers.isEmpty()) listOf(PageBlock.Text("安装 Piper 或其它 TTS 提供者插件后在此管理。"))
                    else providers.map { provider -> PageBlock.Group(provider.name, listOf(
                        PageBlock.Text("${provider.info.voices.size} 个声音 · ${if (provider.info.availability == EngineAvailability.READY) "已就绪" else "需要语音包"}"),
                        PageBlock.Actions(listOf(PageAction("engine/${encode(provider.pluginId)}", "管理引擎与语音包", PluginIcon.SETTINGS))),
                    )) }),
            ))
        }
        if (pageId.startsWith("provider/")) {
            val parts = pageId.split('/')
            val id = decode(parts[1]); val page = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val provider = providers().firstOrNull { it.pluginId == id }
                ?: return PluginPage(pageId, "引擎不可用", listOf(PageBlock.Notice("该插件已停用或卸载，系统 TTS 仍可使用。")))
            val controls = mutableListOf(PageAction("select/${encode(id)}", "使用此引擎", PluginIcon.SPEAKER), PageAction("refresh/${encode(id)}", "刷新声音列表", PluginIcon.INFO))
            if (provider.info.importsVoicePacks) controls += PageAction("import/${encode(id)}", "导入语音包", PluginIcon.ADD)
            val voices = provider.info.voices.drop(page * 20).take(20)
            val navigation = mutableListOf<PageAction>()
            if (page > 0) navigation += PageAction("engine/${encode(id)}/${page - 1}", "上一页")
            if ((page + 1) * 20 < provider.info.voices.size) navigation += PageAction("engine/${encode(id)}/${page + 1}", "下一页")
            return PluginPage(pageId, provider.name, listOf(PageBlock.Text(provider.name, true), PageBlock.Actions(controls)) +
                voices.map { voice ->
                    val current = if (id == SYSTEM_PROVIDER) settings.provider == null && settings.systemVoice == voice.id
                        else settings.provider == id && settings.voice == voice.id
                    val actions = mutableListOf(PageAction("voice/${encode(id)}/${encode(voice.id)}", if (current) "当前声音" else "使用声音", PluginIcon.SPEAKER))
                    if (voice.deletable) {
                        actions += PageAction("rename/${encode(id)}/${encode(voice.id)}", "重命名", PluginIcon.TEXT)
                        actions += PageAction("delete/${encode(id)}/${encode(voice.id)}", "删除语音包", PluginIcon.CLOSE)
                    }
                    PageBlock.Group(voice.name, listOf(PageBlock.Text(voice.language + if (voice.description.isBlank()) "" else " · ${voice.description}"), PageBlock.Actions(actions)))
                } + if (navigation.isEmpty()) emptyList() else listOf(PageBlock.Actions(navigation)))
        }
        return null
    }
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        if (actionId == "library" || actionId.startsWith("pack")) return libraryActions.execute(actionId, context)
        val pieces = actionId.split('/')
        val id = pieces.getOrNull(1)?.let(::decode)
        val voice = pieces.getOrNull(2)?.takeIf { pieces[0] !in setOf("engine") }?.let(::decode)
        return when (pieces[0]) {
            "activate" -> PluginOutcome.SelectTtsService(descriptor.id)
            "system" -> { settings.provider = null; settings.voice = null; settings.save(); PluginOutcome.RefreshUi }
            "engine" -> PluginOutcome.OpenPage("provider/${pieces[1]}/${pieces.getOrNull(2) ?: "0"}")
            "select" -> {
                val provider = providers().first { it.pluginId == id }
                if (id == SYSTEM_PROVIDER) { settings.provider = null; settings.voice = null; settings.save(); return PluginOutcome.RefreshUi }
                settings.provider = id; settings.voice = provider.info.selectedVoiceId ?: provider.info.voices.firstOrNull()?.id
                settings.save(); PluginOutcome.RefreshUi
            }
            "voice" -> {
                if (id == SYSTEM_PROVIDER) {
                    val chosen = system.info.voices.first { it.id == voice }
                    settings.systemVoice = voice; settings.language = chosen.language; settings.provider = null; settings.voice = null; settings.save()
                    return PluginOutcome.RefreshUi
                }
                if (providers().first { it.pluginId == id }.info.managesVoices)
                    broker.command(requireNotNull(id), VoiceCommand(VoiceOperation.SELECT, voiceId = voice))
                settings.provider = id; settings.voice = voice; settings.save(); PluginOutcome.RefreshUi
            }
            "refresh" -> { if (id == SYSTEM_PROVIDER) system.refreshVoices() else broker.command(requireNotNull(id), VoiceCommand(VoiceOperation.REFRESH)); PluginOutcome.RefreshUi }
            "import" -> PluginOutcome.ImportResource("导入语音包", "resources/inbox", "imported/${pieces[1]}")
            "imported" -> {
                val path = requireNotNull(context.inputs["resourcePath"])
                try { broker.command(requireNotNull(id), VoiceCommand(VoiceOperation.IMPORT, resourcePath = path)) }
                finally { environment.storage.file(path).delete() }
                PluginOutcome.RefreshUi
            }
            "rename" -> PluginOutcome.TextInput("语音包名称", "renamed/${pieces[1]}/${pieces[2]}",
                providers().first { it.pluginId == id }.info.voices.first { it.id == voice }.let { it.packName ?: it.name })
            "renamed" -> { broker.command(requireNotNull(id), VoiceCommand(VoiceOperation.RENAME, voiceId = voice, value = context.inputs["value"])); PluginOutcome.RefreshUi }
            "delete" -> PluginOutcome.Menu("确认删除这个语音包及其所有声音？", listOf(PageAction("deleted/${pieces[1]}/${pieces[2]}", "确认删除", PluginIcon.CLOSE)))
            "deleted" -> {
                broker.command(requireNotNull(id), VoiceCommand(VoiceOperation.DELETE, voiceId = voice))
                if (settings.provider == id) { settings.voice = providers().firstOrNull { it.pluginId == id }?.info?.selectedVoiceId; settings.save() }
                PluginOutcome.RefreshUi
            }
            "rate" -> PluginOutcome.TextInput("语速（0.25–4）", "save-rate", settings.rate.toString())
            "save-rate" -> {
                val value = context.inputs["value"]?.toFloatOrNull(); require(value != null && value.isFinite() && value in .25f..4f)
                settings.rate = value; settings.save(); PluginOutcome.RefreshUi
            }
            "language" -> PluginOutcome.TextInput("系统 TTS 语言代码", "save-language", settings.language)
            "save-language" -> {
                val value = context.inputs["value"].orEmpty().trim(); require(value.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*")))
                settings.language = value; settings.systemVoice = null; settings.save(); PluginOutcome.RefreshUi
            }
            else -> PluginOutcome.Message("未知语音合成服务操作")
        }
    }
    private fun providers() = listOf(TtsProvider(SYSTEM_PROVIDER, "内置系统 TTS", system.info)) + broker.providers().filter { it.pluginId != "engine.system-tts" }
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value))
}
