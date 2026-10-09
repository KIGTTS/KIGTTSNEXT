// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.system

import com.kigtts.plugin.api.*
import com.kigtts.plugin.android.AndroidPluginEnvironment
import org.json.JSONObject

/** Adapted from KIGTTS's system TTS module; no host preferences or resources are referenced. */
class SystemTtsPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.system-tts", "系统语音合成", "使用设备已安装的 Android TTS 服务", version = "0.3.0", license = "GPL-3.0-only")
    private lateinit var environment: AndroidPluginEnvironment
    private var language = "zh-CN"
    private var rate = 1f
    private lateinit var engine: SystemSpeechEngine
    override val tts: TtsEngine get() = engine
    override suspend fun attach(environment: PluginEnvironment) {
        this.environment = environment as AndroidPluginEnvironment
        val file = environment.storage.file("config/settings.json")
        if (file.exists()) runCatching {
            val data = JSONObject(file.readText()); language = data.optString("language", "zh-CN")
            rate = data.optDouble("rate", 1.0).toFloat().coerceIn(0.25f, 4f)
        }
        engine = SystemSpeechEngine(this.environment) { language to rate }
    }
    override val contributions get() = PluginContributions(drawerEntries = listOf(
        DrawerEntry("settings", "系统语音合成设置", "settings", PluginIcon.SPEAKER, settingsPage = true)))
    override fun page(pageId: String, context: PluginContext) = if (pageId == "settings") PluginPage(pageId, "系统语音合成", listOf(
        PageBlock.Text("系统 TTS", heading = true),
        PageBlock.Text("语言：$language · 语速：$rate"),
        PageBlock.Text("使用系统默认引擎，失败时尝试其它已安装服务。合成临时 WAV 与设置仅保存在本插件数据目录。"),
        PageBlock.Actions(listOf(PageAction("language", "设置语言", PluginIcon.SETTINGS), PageAction("rate", "设置语速", PluginIcon.SETTINGS))),
    )) else null
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "language" -> PluginOutcome.TextInput("语言代码（如 zh-CN / en-US）", "save-language", language)
        "rate" -> PluginOutcome.TextInput("语速（0.25–4）", "save-rate", rate.toString())
        "save-language" -> {
            val value = context.inputs["value"].orEmpty().trim()
            require(value.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*"))) { "语言代码格式不正确" }
            language = value; save(); PluginOutcome.RefreshUi
        }
        "save-rate" -> {
            val value = context.inputs["value"]?.toFloatOrNull()
            require(value != null && value.isFinite() && value in 0.25f..4f) { "语速应在 0.25–4 之间" }
            rate = value; save(); PluginOutcome.RefreshUi
        }
        else -> PluginOutcome.Message("未知系统 TTS 操作")
    }
    private fun save() {
        environment.storage.configuration
        environment.storage.file("config/settings.json").writeText(JSONObject().put("language", language).put("rate", rate).toString())
    }
}
