package com.kigtts.next.ui.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.PluginOutcome
import com.kigtts.plugin.api.PluginJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PluginExportRequest(model: AppViewModel, request: Pair<String, PluginOutcome.ExportResource>?, dismiss: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var pendingJson by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(remember { ExportDocumentContract() }) { uri ->
        val snapshot = pendingJson?.let { PluginJson.decodeFromString<Pair<String, PluginOutcome.ExportResource>>(it) }; pendingJson = null
        if (snapshot != null) scope.launch {
            val export = snapshot.second
            val source = runCatching { model.pluginManager.paths.existingResource(export.resource.pluginId ?: snapshot.first, export.resource.path) }.getOrNull()
            try {
                if (uri != null) withContext(Dispatchers.IO) {
                    require(source != null && source.isFile && source.length() <= 2L * 1024 * 1024 * 1024)
                    source.inputStream().use { input -> requireNotNull(context.contentResolver.openOutputStream(uri)).use { input.copyTo(it) } }
                }
                if (uri != null) model.message("已导出文件")
            } catch (error: Exception) { model.message(error.message ?: "文件导出失败") }
            finally { if (export.temporary) source?.delete() }
        }
    }
    LaunchedEffect(request) { request?.let { pendingJson = PluginJson.encodeToString(it); picker.launch(it.second); dismiss() } }
}

private class ExportDocumentContract : ActivityResultContract<PluginOutcome.ExportResource, Uri?>() {
    override fun createIntent(context: Context, input: PluginOutcome.ExportResource) = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType(input.mimeType).putExtra(Intent.EXTRA_TITLE, input.fileName)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}
