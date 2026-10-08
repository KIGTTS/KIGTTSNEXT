package com.kigtts.next.state

import android.content.Context
import com.kigtts.plugin.api.*
import com.kigtts.plugins.debug.UiDebugPlugin
import com.kigtts.plugins.listening.ListeningPanelPlugin
import com.kigtts.plugins.voice.VoiceInputPlugin

/** Feature UI plugins depend on host commands; concrete speech engines never appear here. */
internal fun builtinPlugins(context: Context): List<KigPlugin> {
    val debug = context.getSharedPreferences("ui-debug", Context.MODE_PRIVATE)
    val voice = context.getSharedPreferences("voice-input", Context.MODE_PRIVATE)
    return listOf(ListeningPanelPlugin(), VoiceInputPlugin(voice.getBoolean("enabled", false)) {
        voice.edit().putBoolean("enabled", it).apply()
    }, UiDebugPlugin(
        UiSlot.entries.associateWith { debug.getInt(it.name, 0) },
        { counts -> debug.edit().apply { counts.forEach { (slot, count) -> putInt(slot.name, count) } }.apply() },
        UiSlot.entries.associateWith { if (debug.getBoolean(it.name + "_text", false)) UiButtonStyle.ICON_TEXT else UiButtonStyle.ICON },
        { styles -> debug.edit().apply { styles.forEach { (slot, style) -> putBoolean(slot.name + "_text", style == UiButtonStyle.ICON_TEXT) } }.apply() },
    ))
}
