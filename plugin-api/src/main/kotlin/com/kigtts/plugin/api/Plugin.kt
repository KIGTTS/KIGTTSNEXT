package com.kigtts.plugin.api

const val PLUGIN_API_VERSION = 1

data class PluginDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "0.1.0",
    val apiVersion: Int = PLUGIN_API_VERSION,
    val placeholder: Boolean = false,
)

/** UI-free contract: contributions are rendered by the host's Material 3 theme. */
interface KigPlugin {
    val descriptor: PluginDescriptor
    val contributions: PluginContributions get() = PluginContributions()
    fun contributions(context: PluginContext): PluginContributions = contributions
    val tts: TtsEngine? get() = null
    val asr: AsrEngine? get() = null
    suspend fun execute(actionId: String, context: PluginContext): PluginOutcome
    fun page(pageId: String, context: PluginContext): PluginPage? = null
}

data class PluginContext(val editorText: String, val transcript: String, val listening: Boolean = false,
    val listeningPanelVisible: Boolean = listening)

enum class HostCommand { TOGGLE_LISTENING, CLOSE_LISTENING_PANEL, USE_STREAMING, USE_BATCH }

sealed interface PluginOutcome {
    data object RefreshUi : PluginOutcome
    data class HostAction(val command: HostCommand) : PluginOutcome
    data class Message(val text: String) : PluginOutcome
    data class Dialog(val title: String, val text: String) : PluginOutcome
    data class Menu(val title: String, val items: List<PageAction>) : PluginOutcome
    data class OpenPage(val pageId: String) : PluginOutcome
    data class ReplaceEditorText(val text: String) : PluginOutcome
}
