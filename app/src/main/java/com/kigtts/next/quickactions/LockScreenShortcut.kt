package com.kigtts.next.quickactions

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.kigtts.next.MainActivity
import com.kigtts.next.R
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination

private const val CHANNEL_ID = "lock-screen-shortcut"
private const val NOTIFICATION_ID = 0x4B17
private const val ACTION_OPEN = "com.kigtts.next.LOCK_SCREEN_ROUTE"
private const val EXTRA_ROUTE = "route"

internal enum class LockScreenTarget { HOME, ALBUM, SETTINGS }

internal object LockScreenShortcut {
    fun canPost(manager: NotificationManager) = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun notificationSettings(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun sync(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!QuickActionPreferences(context).lockScreenShortcut || !canPost(notifications)) {
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        val channel = NotificationChannel(CHANNEL_ID, context.uiCopy("quick_actions.lock_screen"), NotificationManager.IMPORTANCE_LOW)
            .apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
        notifications.createNotificationChannel(channel)
        val intent = Intent(context, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val summary = context.uiCopy("quick_actions.lock_notification_summary")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_listening)
            .setContentTitle(context.uiCopy("quick_actions.lock_notification_title"))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply {
                if (Build.VERSION.SDK_INT >= 36) setRequestPromotedOngoing(true)
            }
        notifications.notify(NOTIFICATION_ID, notification.build())
    }

    fun mainIntent(context: Context, target: LockScreenTarget) = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN).putExtra(EXTRA_ROUTE, target.name)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deliver(context: Context, intent: Intent?, model: AppViewModel) {
        if (intent?.action != ACTION_OPEN || context.getSystemService(KeyguardManager::class.java).isDeviceLocked) return
        when (intent.getStringExtra(EXTRA_ROUTE)) {
            LockScreenTarget.ALBUM.name -> model.navigate(Destination.Cards)
            LockScreenTarget.SETTINGS.name -> model.navigate(Destination.Settings)
            LockScreenTarget.HOME.name -> model.navigate(Destination.Home)
        }
    }
}

class LockScreenShortcutBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            LockScreenShortcut.sync(context)
    }
}
