package com.kigtts.next.overlay

import com.kigtts.next.i18n.uiCopy

import android.provider.Settings
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** No full-access exception: app-control is not authority to add a user's persistent shortcut. */
internal class PluginOverlayHost(private val manager: PluginManager) {
    private val context = manager.applicationContext
    private val hub = OverlayHub.get(context)
    fun removeOwner(owner: String) = hub.removeOwner(owner)
    suspend fun request(owner: String, capabilities: Set<String>, command: AppCommand.Overlay): AppReply = try {
        require(command.operation in setOf("status", "register", "request-add", "bubble", "clear-bubble")) { manager.applicationContext.uiCopy("overlay.host.unknown") }
        if ("overlay" !in capabilities) AppReply.Failure("PERMISSION_DENIED", manager.applicationContext.uiCopy("overlay.host.no_permission")) else {
            val installed = withContext(Dispatchers.IO) { manager.packages.installed().singleOrNull { it.manifest.id == owner && it.enabled } }
            if (installed == null || manager.safeMode()) AppReply.Failure("UNAVAILABLE", manager.applicationContext.uiCopy("overlay.host.no_plugin"))
            else if (command.operation == "status") data {
                put("enabled", OverlayStore.get(context).settings.value.enabled && Settings.canDrawOverlays(context))
                put("authorized", manager.permissions.access("$owner:${installed.publisherSha256}", "overlay") == com.kigtts.plugin.runtime.PermissionAccess.ALLOW)
                put("userConfirmationRequired", true)
            }
            else if (command.operation == "clear-bubble") { withContext(Dispatchers.Main) { hub.clear(owner) }; data { put("cleared", true) } }
            else if (!manager.requestPermission(owner, "overlay")) AppReply.Failure("PERMISSION_DENIED", manager.applicationContext.uiCopy("overlay.host.permission"))
            else withContext(Dispatchers.Main) {
                when (command.operation) {
                    "register", "request-add" -> {
                        val item = requireNotNull(command.shortcut); require(item.id.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
                        require(item.label.isNotBlank() && item.label.length <= 30 && item.category.length <= 40); symbol(item.symbol); destination(item.actionId, item.pageId)
                        val entry = OverlayShortcut("plugin:$owner:${item.id}", item.label, item.symbol,
                            target = item.pageId?.let { "page:$it" } ?: "action", owner = owner,
                            publisher = installed.publisherSha256, action = item.actionId, showLabel = item.showLabel,
                            category = item.category.ifBlank { installed.manifest.name }, badge = item.badge?.toOverlayBadge())
                        hub.register(entry)
                        val waiting = command.operation == "request-add" && hub.requestAdd(entry)
                        data { put("registered", true); put("pendingUserConfirmation", waiting); put("added", false) }
                    }
                    "bubble" -> {
                        if (!OverlayStore.get(context).settings.value.enabled || !Settings.canDrawOverlays(context))
                            AppReply.Failure("OVERLAY_DISABLED", manager.applicationContext.uiCopy("overlay.host.disabled")) else {
                            val item = requireNotNull(command.bubble); require(item.text.isNotBlank() && item.text.length <= 180); symbol(item.symbol)
                            require((item.actionLabel?.length ?: 0) <= 24)
                            val actionable = item.actionId != null || item.pageId != null
                            if (actionable) { destination(item.actionId, item.pageId); require(!item.actionLabel.isNullOrBlank()) }
                            val shortcut = if (actionable) OverlayShortcut("bubble:$owner", item.actionLabel!!, item.symbol,
                                item.pageId?.let { "page:$it" } ?: "action", owner = owner, publisher = installed.publisherSha256, action = item.actionId) else null
                            hub.show(OverlayNotice(owner, installed.publisherSha256, item.text, item.actionLabel, item.symbol, shortcut), item.durationMs)
                            data { put("shown", true) }
                        }
                    }
                    else -> error("已单独处理")
                }
            }
        }
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: IllegalArgumentException) { AppReply.Failure("INVALID", error.message ?: manager.applicationContext.uiCopy("overlay.host.invalid")) }
    catch (_: Exception) { AppReply.Failure("UNAVAILABLE", manager.applicationContext.uiCopy("overlay.host.unsupported")) }
    private fun symbol(value: String) { require(value.matches(Regex("[a-z0-9_]{1,80}"))) }
    private fun destination(action: String?, page: String?) {
        require((action != null) xor (page != null)) { manager.applicationContext.uiCopy("overlay.host.bad_target") }
        require((action ?: page!!).matches(Regex("[a-zA-Z0-9_.:/-]{1,120}")))
    }
    private fun data(block: JsonObjectBuilder.() -> Unit) = AppReply.Data(buildJsonObject(block))
}
