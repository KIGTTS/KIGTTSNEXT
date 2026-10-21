package com.kigtts.plugin.runtime

import android.content.Context
import com.kigtts.plugin.api.PluginStorage
import java.io.File

class PluginPaths(context: Context) {
    val packages = File(context.filesDir, "kigplugin/packages").apply { mkdirs() }
    val data = File(context.getExternalFilesDir(null) ?: context.filesDir,
        "appdata/Kigtts/plugin/plugindata").apply { mkdirs() }
    fun validateId(id: String) { require(id.matches(Regex("[a-z][a-z0-9.-]{1,79}")) && !id.contains("..")) }
    fun validatePackageId(id: String) { validateId(id); require(id != HOST_BACKUP_ID) { "插件 ID 由宿主备份功能保留" } }
    fun packageDirectory(id: String): File { validatePackageId(id); return File(packages, id) }
    fun storage(id: String): PluginStorage { validateId(id); return PluginStorage(File(data, id)) }
    /** Display/export must never recreate data for an uninstalled plugin. */
    fun existingResource(id: String, relativePath: String): File? {
        validateId(id)
        require(relativePath.isNotBlank() && !relativePath.contains('\\') && !relativePath.contains(':') && !relativePath.contains('\u0000'))
        require(!File(relativePath).isAbsolute && relativePath.split('/').none { it == ".." || it.isEmpty() })
        val root = File(data, id).canonicalFile
        if (!root.isDirectory) return null
        val file = File(root, relativePath).canonicalFile
        require(file != root && file.toPath().startsWith(root.toPath()))
        return file.takeIf { it.isFile }
    }
    companion object { const val HOST_BACKUP_ID = "host.backup" }
}
