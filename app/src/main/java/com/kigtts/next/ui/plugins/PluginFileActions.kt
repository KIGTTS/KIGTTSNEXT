package com.kigtts.next.ui.plugins

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PackagePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PendingPackage(val file: File, val preview: PackagePreview)

internal suspend fun inspectPackage(context: Context, model: AppViewModel, uri: Uri): PendingPackage = withContext(Dispatchers.IO) {
    val file = File(model.pluginManager.paths.packages, ".incoming-${UUID.randomUUID()}.kigplugin")
    try {
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
            var size = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                size += n; require(size <= 768L * 1024 * 1024) { "插件包过大" }; output.write(buffer, 0, n)
            }
        } }
        PendingPackage(file, model.pluginManager.packages.inspect(file))
    } catch (error: Exception) { file.delete(); throw error }
}

internal suspend fun importPluginResource(context: Context, model: AppViewModel, id: String,
    request: PluginOutcome.ImportResource, uri: Uri) {
    val name = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "resource"
    }
    model.stopSpeech()
    val path = model.pluginManager.importResource(id, request.directory, name, request.zip) {
        requireNotNull(context.contentResolver.openInputStream(uri))
    }
    model.refreshPlugins()
    model.invoke(id, request.actionId, mapOf("resourcePath" to path))
}
