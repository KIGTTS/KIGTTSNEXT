package com.kigtts.next.plugins.control

import android.content.Context
import android.net.Uri
import com.kigtts.plugin.api.PluginRegistry
import java.net.URI

/** A link only proposes a source. Fetching/trust/confirmation remain in a source-manager plugin. */
internal class SourceLinkCoordinator(context: Context, private val registry: PluginRegistry,
    private val invoke: (String, String, Map<String, String>, (Boolean) -> Unit) -> Unit,
    private val message: (String) -> Unit, private val available: (String) -> Boolean = { true },
    private val ready: () -> Boolean = { true },
    private val missingStore: () -> Unit = { message("请先安装插件商店，再打开这个链接") }) {
    private val preferences = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
    private var notified = false
    private var attempted = false
    fun accept(uri: Uri?) {
        if (uri == null || uri.scheme != "kigtts" || uri.host != "source") return
        val request = parseRequest(uri) ?: return message("包源链接不合法")
        preferences.edit().putString("url", request.url).putString("package", request.packageId)
            .putBoolean("requireSource", request.requireSource).putString("request", java.util.UUID.randomUUID().toString()).apply()
        notified = false; attempted = false
        deliver()
    }
    fun deliver() {
        val url = preferences.getString("url", null) ?: return
        val packageId = preferences.getString("package", null)
        val request = preferences.getString("request", null)
        if (attempted || !ready()) return
        val plugin = registry.plugins.firstOrNull { registry.available(it) && it.contributions.sourceAction != null }
        if (plugin == null) { if (!notified) { notified = true; missingStore() }; return }
        if (!available(plugin.descriptor.id)) return
        attempted = true
        val inputs = buildMap { put("url", url); put("requireSource", preferences.getBoolean("requireSource", true).toString()); packageId?.let { put("packageId", it) } }
        invoke(plugin.descriptor.id, requireNotNull(plugin.contributions.sourceAction), inputs) { accepted ->
            if (accepted && preferences.getString("url", null) == url && preferences.getString("request", null) == request)
                preferences.edit().remove("url").remove("package").remove("request").remove("requireSource").apply()
            else if (!accepted) message("未能打开内容，请检查插件商店后重试")
        }
    }
    companion object {
        data class Request(val url: String, val packageId: String?, val requireSource: Boolean = true)
        fun parse(uri: Uri): String? = parseRequest(uri)?.url
        fun parseRequest(uri: Uri): Request? = runCatching {
            require(uri.scheme == "kigtts" && uri.host == "source" && uri.path in setOf("/add", "/open"))
            require(uri.fragment == null && uri.getQueryParameters("url").size == 1 && uri.getQueryParameters("package").size <= 1 && uri.getQueryParameters("addSource").size <= 1)
            val url = requireNotNull(uri.getQueryParameter("url")); require(url.length in 1..4096)
            val target = URI(url); require(target.scheme == "https" && !target.host.isNullOrBlank() && target.userInfo == null && target.fragment == null)
            val packageId = uri.getQueryParameter("package")
            require(packageId == null || packageId.matches(Regex("[a-z][a-z0-9.-]{0,63}")))
            val mode = uri.getQueryParameter("addSource")
            require(mode == null || mode in setOf("required", "optional"))
            require(uri.path != "/open" || packageId != null)
            Request(url, packageId, mode?.let { it == "required" } ?: (uri.path == "/add"))
        }.getOrNull()
    }
}
