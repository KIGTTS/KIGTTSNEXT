package com.kigtts.next.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kigtts.next.media.WebAndroidPermissions
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Caller-scoped channels, IDs and tap routes. Notification permission is independent of app-control. */
internal class PluginNotifications(private val manager: PluginManager) {
    private val context = manager.applicationContext
    private val system = context.getSystemService(NotificationManager::class.java)
    private val routes = NotificationRoutes(context)
    suspend fun request(owner: String, capabilities: Set<String>, command: AppCommand.Notifications): AppReply { return try {
        require(command.operation in setOf("status", "post", "cancel", "cancel-all")) { "未知通知操作" }
        if ("notifications" !in capabilities) return failure("PERMISSION_DENIED", "插件未声明通知权限")
        val installed = withContext(Dispatchers.IO) { manager.packages.installed().singleOrNull { it.manifest.id == owner && it.enabled } }
            ?: return failure("UNAVAILABLE", "插件未安装或已停用")
        if (command.operation == "status") return data {
            put("enabled", system.areNotificationsEnabled()); put("liveSupported", Build.VERSION.SDK_INT >= 36)
        }
        if (command.operation == "cancel-all") { cancelAll(owner); return data { put("cancelled", true) } }
        if (command.operation == "cancel") {
            val id = requireNotNull(command.id); validateId(id)
            system.cancel(tag(owner, id), 1); routes.revoke(owner, id); return data { put("cancelled", true) }
        }
        val item = requireNotNull(command.notification) { "缺少通知内容" }; validate(item)
        if (!manager.requestPermission(owner, "notifications")) return failure("PERMISSION_DENIED", "插件通知权限尚未授权或已禁用")
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (!manager.permissions.foreground) return failure("SYSTEM_PERMISSION_REQUIRED", "请在KIGTTS前台开启通知权限")
            if (!WebAndroidPermissions.request(context, listOf(Manifest.permission.POST_NOTIFICATIONS))) return failure("PERMISSION_DENIED", "系统通知权限未开启")
        }
        if (!system.areNotificationsEnabled()) return failure("NOTIFICATIONS_DISABLED", "系统设置已关闭KIGTTS通知")
        val channel = "plugin:$owner:${item.kind.name.lowercase()}"
        system.createNotificationChannel(NotificationChannel(channel, "${installed.manifest.name} · ${when(item.kind) {
            PluginNotificationKind.MESSAGE -> "消息"; PluginNotificationKind.PROGRESS -> "任务进度"; PluginNotificationKind.SHORTCUT -> "快捷入口"
        }}", if (item.kind == PluginNotificationKind.MESSAGE) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW))
        if (system.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return failure("CHANNEL_DISABLED", "此插件通知类别已关闭")
        val prefix = "plugin:$owner:"
        require(system.activeNotifications.count { it.tag?.startsWith(prefix) == true && it.tag != tag(owner, item.id) } < 8) { "每个插件最多显示8条通知" }
        routes.begin(owner, item.id)
        val builder = NotificationCompat.Builder(context, channel).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.title).setContentText(item.text).setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setSubText(installed.manifest.name).setOnlyAlertOnce(true).setOngoing(item.ongoing).setAutoCancel(!item.ongoing)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(routes.intent(owner, installed.publisherSha256, item.id, item.contentAction ?: PluginNotificationAction("打开")))
        if (item.kind == PluginNotificationKind.PROGRESS) {
            builder.setCategory(NotificationCompat.CATEGORY_PROGRESS).setProgress(item.progressMax, item.progress ?: 0, item.indeterminate)
            if (Build.VERSION.SDK_INT >= 36) builder.setStyle(NotificationCompat.ProgressStyle()
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(item.progressMax)))
                .setProgress(item.progress ?: 0).setProgressIndeterminate(item.indeterminate))
        }
        item.symbol?.let { notificationSymbol(it)?.let(builder::setSmallIcon) }
        if (Build.VERSION.SDK_INT >= 36) builder.setRequestPromotedOngoing(item.live)
        item.actions.forEach { action -> builder.addAction(NotificationCompat.Action.Builder(0, action.label,
            routes.intent(owner, installed.publisherSha256, item.id, action)).build()) }
        system.notify(tag(owner, item.id), 1, builder.build())
        data { put("posted", true); put("id", item.id); put("liveRequested", item.live && Build.VERSION.SDK_INT >= 36) }
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: IllegalArgumentException) { failure("INVALID", error.message ?: "通知参数无效") }
    catch (_: Exception) { failure("UNAVAILABLE", "通知暂时无法发送") }
    }

    fun cancelAll(owner: String) {
        system.activeNotifications.filter { it.tag?.startsWith("plugin:$owner:") == true }.forEach { system.cancel(it.tag, it.id) }
        routes.revokeOwner(owner)
    }
    private fun validate(item: PluginNotification) {
        validateId(item.id)
        require(item.title.isNotBlank() && item.title.length <= 160 && item.text.length <= 4000 && item.actions.size <= 3)
        require(item.progressMax in 1..100_000 && (item.progress == null || item.progress in 0..item.progressMax))
        require(!item.live || item.kind == PluginNotificationKind.PROGRESS && item.ongoing) { "实况通知只能用于正在进行的任务" }
        item.symbol?.let { require(it.matches(Regex("[a-z][a-z0-9_]{0,79}"))) }
        (item.actions + listOfNotNull(item.contentAction)).forEach { action ->
            require(action.label.isNotBlank() && action.label.length <= 40 && action.target in setOf("plugin", "home", "quick-text", "cards", "sketch"))
            require(listOfNotNull(action.pageId, action.actionId, action.phraseId).size <= 1)
            listOfNotNull(action.pageId, action.actionId, action.phraseId).forEach { require(it.isNotBlank() && it.length <= 160) }
            require(action.target == "plugin" || action.pageId == null && action.actionId == null && action.phraseId == null)
        }
    }
    private fun notificationSymbol(name: String): androidx.core.graphics.drawable.IconCompat? = runCatching {
        com.kigtts.next.ui.components.MaterialSymbols.initialize(context)
        val path = com.kigtts.next.ui.components.MaterialSymbols.androidPath(name) ?: return@runCatching null
        val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply { scale(2f, 2f); drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }) }
        androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
    }.getOrNull()
    private fun validateId(id: String) { require(id.matches(Regex("[A-Za-z0-9_.:-]{1,80}"))) { "通知ID无效" } }
    private fun tag(owner: String, id: String) = "plugin:$owner:$id"
    private fun data(body: JsonObjectBuilder.() -> Unit) = AppReply.Data(buildJsonObject(body))
    private fun failure(code: String, message: String) = AppReply.Failure(code, message)
}
