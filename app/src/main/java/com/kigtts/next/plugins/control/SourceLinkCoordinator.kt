package com.kigtts.next.plugins.control

import android.content.Context
import android.net.Uri
import com.kigtts.plugin.api.PluginRegistry
import java.net.URI

/** A link only proposes a source. Fetching/trust/confirmation remain in a source-manager plugin. */
internal class SourceLinkCoordinator(context: Context, private val registry: PluginRegistry,
    private val invoke: (String, String, Map<String, String>, (Boolean) -> Unit) -> Unit,
    private val message: (String) -> Unit, private val available: (String) -> Boolean = { true }) {
    private val preferences = context.getSharedPreferences("pending-package-source", Context.MODE_PRIVATE)
    private var notified = false
    private var attempted = false
    fun accept(uri: Uri?) {
        if (uri == null) return
        val url = parse(uri) ?: return message("包源链接不合法")
        preferences.edit().putString("url", url).apply(); notified = false; attempted = false
        deliver()
    }
    fun deliver() {
        val url = preferences.getString("url", null) ?: return
        if (attempted) return
        val plugin = registry.plugins.firstOrNull { registry.available(it) && it.contributions.sourceAction != null }
        if (plugin == null) { if (!notified) { message("请先安装插件商店，再添加这个源"); notified = true }; return }
        if (!available(plugin.descriptor.id)) return
        attempted = true
        invoke(plugin.descriptor.id, requireNotNull(plugin.contributions.sourceAction), mapOf("url" to url)) { accepted ->
            if (accepted && preferences.getString("url", null) == url) preferences.edit().remove("url").apply()
            else if (!accepted) message("源尚未添加，请检查插件商店后重新打开链接")
        }
    }
    companion object {
        fun parse(uri: Uri): String? = runCatching {
            require(uri.scheme == "kigtts" && uri.host == "source" && uri.path == "/add")
            val url = requireNotNull(uri.getQueryParameter("url")); require(url.length in 1..4096)
            val target = URI(url); require(target.scheme == "https" && !target.host.isNullOrBlank() && target.userInfo == null && target.fragment == null)
            url
        }.getOrNull()
    }
}
