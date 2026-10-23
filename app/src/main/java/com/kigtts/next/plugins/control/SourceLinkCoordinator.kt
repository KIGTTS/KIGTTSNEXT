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
    private val missingStore: () -> Unit = { message("请先安装插件商店，再添加这个源") }) {
    private val preferences = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
    private var notified = false
    private var attempted = false
    fun accept(uri: Uri?) {
        if (uri == null) return
        val request = parseRequest(uri) ?: return message("包源链接不合法")
        preferences.edit().putString("url", request.url).putString("package", request.packageId)
            .putString("request", java.util.UUID.randomUUID().toString()).apply()
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
        val inputs = buildMap { put("url", url); packageId?.let { put("packageId", it) } }
        invoke(plugin.descriptor.id, requireNotNull(plugin.contributions.sourceAction), inputs) { accepted ->
            if (accepted && preferences.getString("url", null) == url && preferences.getString("request", null) == request)
                preferences.edit().remove("url").remove("package").remove("request").apply()
            else if (!accepted) message("源尚未添加，请检查插件商店后重新打开链接")
        }
    }
    companion object {
        data class Request(val url: String, val packageId: String?)
        fun parse(uri: Uri): String? = parseRequest(uri)?.url
        fun parseRequest(uri: Uri): Request? = runCatching {
            require(uri.scheme == "kigtts" && uri.host == "source" && uri.path == "/add")
            require(uri.fragment == null && uri.getQueryParameters("url").size == 1 && uri.getQueryParameters("package").size <= 1)
            val url = requireNotNull(uri.getQueryParameter("url")); require(url.length in 1..4096)
            val target = URI(url); require(target.scheme == "https" && !target.host.isNullOrBlank() && target.userInfo == null && target.fragment == null)
            val packageId = uri.getQueryParameter("package")
            require(packageId == null || packageId.matches(Regex("[a-z][a-z0-9.-]{0,63}")))
            Request(url, packageId)
        }.getOrNull()
    }
}
