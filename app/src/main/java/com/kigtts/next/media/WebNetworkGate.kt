package com.kigtts.next.media

import com.kigtts.plugin.runtime.PermissionAccess
import com.kigtts.plugin.runtime.PluginPermissions
import kotlinx.coroutines.runBlocking
import java.net.URI

/** One-time web grants cover this WebView load, including its styles, images and fetch requests. */
class WebNetworkGate(private val permissions: PluginPermissions, private val owner: String, private val name: String,
    private val active: () -> Boolean, private val purpose: String = "此页面需要加载外部内容或向网络服务发送请求。授权仅适用于此词条，可在词条编辑中关闭。") {
    @Volatile private var once = false
    fun granted(): Boolean = when (permissions.access(owner, "network")) { PermissionAccess.ALLOW -> true; PermissionAccess.DENY -> false; PermissionAccess.ASK -> once }
    @Synchronized fun authorize(url: String): Boolean {
        if (!active()) return false
        when (permissions.access(owner, "network")) {
            PermissionAccess.DENY -> return false
            PermissionAccess.ALLOW -> return true
            PermissionAccess.ASK -> if (once) return true
        }
        return runBlocking { permissions.request(owner, "network", name,
            purpose, URI(url).host.orEmpty()) }.also { once = it }
    }
    companion object { fun owner(entryId: String) = "quick-page:$entryId" }
}
