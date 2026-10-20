package com.kigtts.plugin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

/** A connection-bound capability. JSON cannot impersonate another plugin or choose its storage owner. */
interface AppBroker { suspend fun request(command: AppCommand): AppReply }

@Serializable sealed interface AppCommand {
    @Serializable @SerialName("describe") data object Describe : AppCommand
    @Serializable @SerialName("state") data object State : AppCommand
    @Serializable @SerialName("navigate") data class Navigate(val target: String) : AppCommand
    @Serializable @SerialName("text") data class SetText(val text: String) : AppCommand
    @Serializable @SerialName("automatic-read") data class SetAutomaticRead(val enabled: Boolean) : AppCommand
    @Serializable @SerialName("speak") data object Speak : AppCommand
    @Serializable @SerialName("stop-speech") data object StopSpeech : AppCommand
    @Serializable @SerialName("setting") data class Setting(val key: String, val value: String) : AppCommand
    @Serializable @SerialName("plugin-action") data class PluginAction(val pluginId: String, val actionId: String,
        val inputs: Map<String, String> = emptyMap()) : AppCommand
    @Serializable @SerialName("plugin-call") data class PluginCall(val pluginId: String, val actionId: String,
        val inputs: Map<String, String> = emptyMap()) : AppCommand
    /** Copy a scoped draft into the recipient's inbox. The recipient still owns review and installation. */
    @Serializable @SerialName("deliver-resource") data class DeliverResource(val pluginId: String,
        val source: PluginResource, val actionId: String) : AppCommand
}
@Serializable sealed interface AppReply {
    @Serializable @SerialName("data") data class Data(val value: JsonObject) : AppReply
    @Serializable @SerialName("failure") data class Failure(val code: String, val message: String) : AppReply
}
