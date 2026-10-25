package com.kigtts.next.ui.plugins

import androidx.compose.runtime.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.artwork.LibraryArtworkSelector
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun PluginArtworkRequest(model: AppViewModel, value: Pair<String, PluginOutcome.SelectArtwork>?, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    value?.let { (owner, outcome) ->
        val request = outcome.request
        LibraryArtworkSelector(model, request, {
            close(); request.cancelActionId?.let { action -> scope.launch {
                while (owner in model.state.value.busyPlugins) yield()
                model.invoke(owner, action, mapOf("artworks" to "[]"))
            } }
        }) { ids ->
            close()
            scope.launch {
                try {
                    val payload = withContext(Dispatchers.IO) {
                        val storage = model.pluginManager.paths.storage(owner)
                        val batch = request.directory.trimEnd('/') + "/" + java.util.UUID.randomUUID()
                        val result = JSONArray(); var bytes = 0L
                        try {
                            ids.forEach { id ->
                                val file = com.kigtts.next.artwork.artworkImage(model, request.library, id)
                                bytes += file.length(); require(bytes <= 128L * 1024 * 1024) { "所选图片过大，请分批选择" }
                                val path = "$batch/$id.png"; file.copyTo(storage.file(path).apply { parentFile!!.mkdirs() })
                                result.put(JSONObject().put("id", id).put("library", request.library.name).put("resourcePath", path))
                            }
                            result.toString()
                        } catch (failure: Exception) { storage.file(batch).deleteRecursively(); throw failure }
                    }
                    while (owner in model.state.value.busyPlugins) yield()
                    model.invoke(owner, request.actionId, mapOf("artworks" to payload))
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { model.message(error.message ?: "作品未能打开，请重试") }
            }
        }
    }
}
