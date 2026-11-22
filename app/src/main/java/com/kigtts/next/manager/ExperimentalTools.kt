package com.kigtts.next.manager

import android.content.Context
import androidx.core.content.edit
import com.kigtts.plugin.runtime.BundledPlugin

internal enum class ExperimentalTool(val id: String, val key: String, val versionCode: Int) {
    ASSISTANT("tools.cloud-agent", "assistant", 36),
    EDITOR("runtime.lua", "editor", 14);

    companion object {
        fun preferences(context: Context) = context.getSharedPreferences("experiments", Context.MODE_PRIVATE)
        fun enabled(context: Context, id: String) = preferences(context).getBoolean("bundle/$id", false)
        fun setEnabled(context: Context, tool: ExperimentalTool, enabled: Boolean) =
            preferences(context).edit { putBoolean("bundle/${tool.id}", enabled) }
        val bundles = entries.map { tool -> BundledPlugin(tool.id, "experimental/${tool.key}.kigplugin", tool.versionCode,
            "b5927c76c21539ef99352707c215b3ce48f3fed937a6f995d39a3c677a052ab5") }
    }
}
