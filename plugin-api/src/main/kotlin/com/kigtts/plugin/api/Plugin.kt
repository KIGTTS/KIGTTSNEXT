package com.kigtts.plugin.api

import kotlinx.serialization.Serializable

const val PLUGIN_API_VERSION = 4

@Serializable
data class PluginDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "0.1.0",
    val apiVersion: Int = PLUGIN_API_VERSION,
    val placeholder: Boolean = false,
    val license: String = "MIT",
)

/** UI-free contract: contributions are rendered by the host's Material 3 theme. */
interface KigPlugin {
    val descriptor: PluginDescriptor
    val contributions: PluginContributions get() = PluginContributions()
    fun contributions(context: PluginContext): PluginContributions = contributions
    val tts: TtsEngine? get() = null
    val asr: AsrEngine? get() = null
    val voiceManager: VoiceManager? get() = null
    suspend fun execute(actionId: String, context: PluginContext): PluginOutcome
    fun page(pageId: String, context: PluginContext): PluginPage? = null
    /** Called once before any other operation. All mutable files belong to this environment. */
    suspend fun attach(environment: PluginEnvironment) {}
    /** Host cancels and joins active sessions before closing/unloading a plugin. */
    suspend fun close() { tts?.close(); asr?.close() }
}

@Serializable
data class PluginContext(val editorText: String, val transcript: String, val listening: Boolean = false,
    val listeningPanelVisible: Boolean = listening, val inputs: Map<String, String> = emptyMap())

@Serializable
enum class HostCommand { TOGGLE_LISTENING, CLOSE_LISTENING_PANEL, USE_STREAMING, USE_BATCH, VOICE_INPUT }

@Serializable
sealed interface PluginOutcome {
    @Serializable
    data class SelectTtsService(val pluginId: String) : PluginOutcome
    @Serializable
    data object RefreshUi : PluginOutcome
    @Serializable
    data class HostAction(val command: HostCommand) : PluginOutcome
    @Serializable
    data class Message(val text: String) : PluginOutcome
    @Serializable
    data class Dialog(val title: String, val text: String) : PluginOutcome
    @Serializable
    data class Menu(val title: String, val items: List<PageAction>) : PluginOutcome
    @Serializable
    data class OpenPage(val pageId: String) : PluginOutcome
    @Serializable
    data class ReplaceEditorText(val text: String) : PluginOutcome
    /** Host chooses and copies a document; callback receives inputs["resourcePath"]. */
    @Serializable
    data class ImportResource(val title: String, val directory: String, val actionId: String,
        val zip: Boolean = false, val mimeTypes: List<String> = listOf("*/*")) : PluginOutcome
    /** Host renders a native input dialog; callback receives inputs["value"]. */
    @Serializable
    data class TextInput(val title: String, val actionId: String, val initialValue: String = "", val multiline: Boolean = false) : PluginOutcome
    @Serializable
    data class ExportResource(val title: String, val resource: PluginResource, val fileName: String,
        val mimeType: String = "application/octet-stream", val temporary: Boolean = false) : PluginOutcome
}
