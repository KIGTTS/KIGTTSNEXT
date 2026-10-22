package com.kigtts.next.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kigtts.next.MainActivity
import com.kigtts.plugin.api.*
import kotlinx.serialization.json.*
import java.util.UUID

internal data class NotificationRoute(val owner: String, val publisher: String, val action: PluginNotificationAction)

/** Random, persisted capabilities let notification taps survive process death without trusting Intent parameters. */
internal class NotificationRoutes(private val context: Context) {
    private val preferences = context.getSharedPreferences("plugin-notification-routes", Context.MODE_PRIVATE)
    fun begin(owner: String, id: String) = revoke(owner, id)
    fun intent(owner: String, publisher: String, id: String, action: PluginNotificationAction): PendingIntent = synchronized(lock) {
        val token = UUID.randomUUID().toString()
        val slot = slot(owner, id)
        val tokens = preferences.getStringSet(slot, emptySet()).orEmpty() + token
        val record = buildJsonObject { put("owner", owner); put("publisher", publisher); put("action", PluginJson.encodeToJsonElement(PluginNotificationAction.serializer(), action)) }
        preferences.edit().putStringSet(slot, tokens).putString("route|$token", record.toString()).apply()
        val intent = Intent(context, MainActivity::class.java).setAction(ACTION)
            .setData(Uri.parse("kigtts-notification://open/$token")).putExtra(EXTRA, token)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun resolve(intent: Intent?): NotificationRoute? {
        if (intent?.action != ACTION) return null
        val token = intent.getStringExtra(EXTRA)?.takeIf { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) } ?: return null
        return preferences.getString("route|$token", null)?.let { runCatching {
            val record = PluginJson.parseToJsonElement(it).jsonObject
            NotificationRoute(record.getValue("owner").jsonPrimitive.content, record.getValue("publisher").jsonPrimitive.content,
                PluginJson.decodeFromJsonElement(PluginNotificationAction.serializer(), record.getValue("action")))
        }.getOrNull() }
    }
    fun revoke(owner: String, id: String) = synchronized(lock) {
        val key = slot(owner, id)
        preferences.edit().also { edit -> preferences.getStringSet(key, emptySet()).orEmpty().forEach { edit.remove("route|$it") }; edit.remove(key) }.apply()
    }
    fun revokeOwner(owner: String) = synchronized(lock) {
        preferences.all.keys.filter { it.startsWith("slot|$owner|") }.forEach { revoke(owner, it.substringAfter("slot|$owner|")) }
    }
    companion object {
        private val lock = Any()
        private const val ACTION = "com.kigtts.next.OPEN_PLUGIN_NOTIFICATION"
        private const val EXTRA = "notification-token"
        private fun slot(owner: String, id: String) = "slot|$owner|$id"
    }
}
