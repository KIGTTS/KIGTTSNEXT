package com.kigtts.next.media

import android.Manifest
import android.os.Build
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kigtts.next.i18n.uiCopy
import com.kigtts.plugin.runtime.PluginPermissions
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/** Device grants belong to an entry and origin; the Android grant alone never authorizes a webpage. */
internal class WebPagePrivileges(private val web: WebView, private val ledger: PluginPermissions, private val entry: String,
    private val name: String, private val scope: CoroutineScope, private val active: () -> Boolean,
    private val network: (String) -> Boolean, private val presentation: WebPresentationSession? = null) {
    private val requests = mutableMapOf<PermissionRequest, Job>()
    private val motion = WebMotion(web) { active() }
    private var motionWanted = false
    private fun origin(value: String?): String? = runCatching { URI(value.orEmpty()).let { require(it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null); "${it.scheme}://${it.host}${if(it.port>=0) ":${it.port}" else ""}" } }.getOrNull()
    private suspend fun allow(permission: String, requestedOrigin: String? = origin(web.url)): Boolean {
        val current = origin(web.url) ?: return false
        if (!active() || requestedOrigin != current) return false
        val hash = MessageDigest.getInstance("SHA-256").digest(current.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        val owner = "web-device:${entry.take(120)}:$hash"
        val purpose = when(permission) {
            "camera" -> "此页面请求使用摄像头，仅当前词条和网站可使用此授权。"
            "microphone" -> "此页面请求录制麦克风声音，仅当前词条和网站可使用此授权。"
            "motion-sensors" -> "此页面请求读取设备运动、重力和方向数据。"
            "web-presentation" -> web.context.uiCopy("permission.web-presentation-purpose")
            else -> "此页面请求将文件保存到系统下载目录。"
        }
        return ledger.request(owner, permission, name.take(60), purpose, current) && active() && origin(web.url) == current
    }
    private suspend fun storageAllowed() = Build.VERSION.SDK_INT >= 29 || WebAndroidPermissions.request(web.context, listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    fun install() {
        val previous = web.webChromeClient
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage) = previous?.onConsoleMessage(message) ?: true
            override fun onPermissionRequest(request: PermissionRequest) {
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val grants = mutableListOf<String>()
                        for (resource in request.resources) {
                            val pair = when(resource) {
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "camera" to Manifest.permission.CAMERA
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "microphone" to Manifest.permission.RECORD_AUDIO
                                else -> null
                            } ?: continue
                            if (allow(pair.first, origin(request.origin.toString())) && WebAndroidPermissions.request(web.context, listOf(pair.second))) grants += resource
                        }
                        if (active() && grants.isNotEmpty()) request.grant(grants.toTypedArray()) else request.deny()
                    } catch (cancel: CancellationException) { runCatching { request.deny() }; throw cancel }
                    catch (_: Exception) { runCatching { request.deny() } }
                    finally { requests.remove(request) }
                }
                requests[request] = job; job.start()
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { requests.remove(request)?.cancel() }
        }
        web.setDownloadListener { url, _, disposition, mime, _ -> scope.launch {
            try { if (allow("downloads") && storageAllowed() && withContext(Dispatchers.IO) { network(url) }) WebDownloads.remote(web.context, url, disposition, mime) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Download errors never escape the WebView callback. */ }
        } }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addWebMessageListener(web, "KigttsDeviceConsent", setOf("*")) { _, message, sourceOrigin, mainFrame, reply ->
                if (!mainFrame || !active() || origin(sourceOrigin.toString()) != origin(web.url)) return@addWebMessageListener
                val raw = message.data.orEmpty(); if (raw.length > 12 * 1024 * 1024) return@addWebMessageListener
                scope.launch {
                    val data = runCatching { JSONObject(raw) }.getOrNull() ?: return@launch
                    val result = JSONObject().put("id", data.optString("id").take(40))
                    try {
                        when (data.optString("operation")) {
                            "document-start" -> { motionWanted = false; motion.stop(); result.put("ok", true) }
                            "presentation" -> {
                                val expected = origin(sourceOrigin.toString())
                                val allowed = raw.length <= 6 * 1024 * 1024 && presentation?.apply(data, expected == WebContentPolicy.ORIGIN,
                                    { active() && origin(web.url) == expected }) { allow("web-presentation", expected) } == true
                                result.put("ok", allowed)
                            }
                            "clear-presentation" -> { presentation?.clear(); result.put("ok", presentation != null) }
                            "request-fullscreen", "exit-fullscreen" -> {
                                val enabled = data.getString("operation") == "request-fullscreen"
                                val expected = origin(sourceOrigin.toString())
                                val allowed = expected != null && presentation?.fullscreen(enabled,
                                    { active() && origin(web.url) == expected }) { allow("web-presentation", expected) } == true
                                result.put("ok", allowed)
                            }
                            "motion" -> { val allowed = allow("motion-sensors"); motionWanted = allowed && motion.start(); result.put("ok", motionWanted) }
                            "download-url" -> {
                                val url = data.getString("url"); require(WebContentPolicy.https(url) && URI(url).userInfo == null)
                                val allowed = allow("downloads") && storageAllowed() && withContext(Dispatchers.IO) { network(url) }
                                if (allowed) WebDownloads.remote(web.context, url, null, data.optString("mime")); result.put("ok", allowed)
                            }
                            "download-data" -> {
                                val value = data.getString("data"); require(value.length <= 12 * 1024 * 1024 && value.startsWith("data:") && value.substringBefore(',').endsWith(";base64"))
                                val allowed = allow("downloads") && storageAllowed()
                                if (allowed) {
                                    val bytes = android.util.Base64.decode(value.substringAfter(','), android.util.Base64.DEFAULT)
                                    require(bytes.size <= 8 * 1024 * 1024)
                                    WebDownloads.data(web.context, data.optString("name", "download"), data.optString("mime", "application/octet-stream"), bytes)
                                }; result.put("ok", allowed)
                            }
                            else -> error("不支持的网页权限请求")
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { result.put("ok", false).put("error", "请求未完成或权限被拒绝") }
                    if (active()) reply.postMessage(result.toString())
                }
            }
            WebViewCompat.addDocumentStartJavaScript(web, webDeviceScript(presentation != null), setOf("*"))
        }
    }
    fun pause() { presentation?.clear(); motion.stop(); web.evaluateJavascript("window.__kigttsStopDeviceTracks?.()", null); requests.keys.toList().forEach { request -> requests.remove(request)?.cancel(); runCatching { request.deny() } } }
    fun resume() { if (motionWanted && active()) motion.start() }
    fun close() { motionWanted = false; pause() }
}
