package com.kigtts.plugin.runtime

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*

/** Keeps an active cloud transfer visible; no idle or boot-time residency. */
internal class PluginTaskForeground(private val service: Service) {
    private var active = false
    private var wake: PowerManager.WakeLock? = null
    fun update(running: Boolean, title: String) {
        if (running == active) return
        if (!running) { close(); return }
        val notifications = service.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("plugin-tasks", "助手后台任务", NotificationManager.IMPORTANCE_LOW))
        val launch = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: Intent()
        val open = PendingIntent.getActivity(service, 20, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(service, "plugin-tasks").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title).setContentText("正在生成，点按返回 KIGTTS").setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) service.startForeground(220, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else service.startForeground(220, notification)
        active = true
        wake = service.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KIGTTS:Agent").apply {
            setReferenceCounted(false); acquire(10 * 60 * 1000L)
        }
    }
    fun close() {
        wake?.let { if (it.isHeld) it.release() }; wake = null
        if (active) service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        active = false
    }
}
