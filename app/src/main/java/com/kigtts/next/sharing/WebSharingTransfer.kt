package com.kigtts.next.sharing

import android.util.Base64
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kigtts.next.media.WebContentPolicy
import com.kigtts.next.state.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Streaming, acknowledged chunks avoid the WebView URL/download size limits. Local sharing pages only. */
internal class WebSharingTransfer(private val model: AppViewModel, private val owner: String,
    private val web: WebView, private val scope: CoroutineScope, private val active: () -> Boolean,
    private val completed: ((File, String, String?) -> Unit)? = null) : AutoCloseable {
    private val mutex = Mutex()
    private var output: FileOutputStream? = null
    private var target: File? = null
    private var relative: String? = null
    private var transferKey: String? = null
    private var expected = 0L
    private var count = 0L
    private var closed = false
    fun install() {
        if (!model.sharing.isProvider(owner) || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(web, "KigttsTransfer", setOf(WebContentPolicy.ORIGIN)) { _, message, origin, main, reply ->
            if (closed || !main || !active() || origin.toString() != WebContentPolicy.ORIGIN) return@addWebMessageListener
            val raw = message.data.orEmpty(); if (raw.length > 64*1024) return@addWebMessageListener
            scope.launch {
                var id = ""
                val response = try {
                    val input = JSONObject(raw); id = input.getString("id").also { require(it.length <= 80) }
                    mutex.withLock { withContext(Dispatchers.IO) { require(!closed && active()); operation(input) } }
                    JSONObject().put("id", id).put("ok", true)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    mutex.withLock { withContext(Dispatchers.IO) { abort() } }
                    JSONObject().put("id", id).put("ok", false).put("error", "文件未能保存，请重试")
                }
                if (!closed) reply.postMessage(response.toString())
            }
        }
    }
    private fun operation(input: JSONObject) {
        when (input.getString("op")) {
            "begin" -> {
                check(output == null)
                val name = input.getString("name").also { require(it.matches(Regex("[^/\\\\:\\p{Cntrl}]{1,160}")) && it !in setOf(".", "..")) }
                expected = input.getLong("bytes").also { require(it in 1..512L*1024*1024) }; count = 0
                transferKey = input.optString("key").takeIf { it.length in 1..80 }
                relative = "incoming/${UUID.randomUUID()}/$name"
                target = model.pluginManager.paths.storage(owner).file(relative!!).also { it.parentFile!!.mkdirs() }
                output = FileOutputStream(target)
            }
            "chunk" -> {
                check(input.getLong("offset") == count)
                val value = input.getString("data"); require(value.length <= 44_000)
                val bytes = Base64.decode(value, Base64.NO_WRAP); require(bytes.size in 1..32768 && count + bytes.size <= expected)
                requireNotNull(output).write(bytes); count += bytes.size
            }
            "finish" -> {
                check(output != null && count == expected); output!!.close(); output = null
                val file = requireNotNull(target); val path = requireNotNull(relative); target = null; relative = null
                val key = transferKey; transferKey = null
                scope.launch(Dispatchers.Main) { completed?.invoke(file, path, key) ?: model.sharing.received(owner, file, path) }
            }
            "cancel" -> abort()
            else -> error("Unknown transfer operation")
        }
    }
    private fun abort() { runCatching { output?.close() }; output = null; target?.delete(); target = null; relative = null; count = 0 }
    override fun close() { closed = true; scope.launch(Dispatchers.IO + NonCancellable) { mutex.withLock { abort() } } }
}
