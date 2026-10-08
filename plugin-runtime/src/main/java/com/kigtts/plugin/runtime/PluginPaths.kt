package com.kigtts.plugin.runtime

import android.content.Context
import com.kigtts.plugin.api.PluginStorage
import java.io.File

class PluginPaths(context: Context) {
    val packages = File(context.filesDir, "kigplugin/packages").apply { mkdirs() }
    val data = File(context.getExternalFilesDir(null) ?: context.filesDir,
        "appdata/Kigtts/plugin/plugindata").apply { mkdirs() }
    fun validateId(id: String) { require(id.matches(Regex("[a-z][a-z0-9.-]{1,79}")) && !id.contains("..")) }
    fun packageDirectory(id: String): File { validateId(id); return File(packages, id) }
    fun storage(id: String): PluginStorage { validateId(id); return PluginStorage(File(data, id)) }
}
