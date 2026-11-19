package com.kigtts.next.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kigtts.next.R

/** A notification tap can authorize microphone startup after opening a window entirely in the background. */
internal class CaptionStartNotification(private val context: Context, private val copy: (String) -> String,
    private val start: () -> Unit) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var pending: PendingIntent? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (pending == null || intent.action != ACTION) return
            clear()
            start()
        }
    }

    fun show(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!notifications.areNotificationsEnabled()) return false
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, copy("listening.window_group"), NotificationManager.IMPORTANCE_DEFAULT))
        if (notifications.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = pending ?: PendingIntent.getBroadcast(context, ID, Intent(ACTION).setPackage(context.packageName),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE).also {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            pending = it
        }
        notifications.notify(ID, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_listening)
            .setContentTitle(copy("listening.start")).setContentText(copy("listening.window_group"))
            .setContentIntent(intent).setAutoCancel(true).build())
        return true
    }

    fun clear() {
        val intent = pending ?: return
        pending = null
        intent.cancel()
        context.unregisterReceiver(receiver)
        notifications.cancel(ID)
    }

    private companion object {
        const val ID = 105
        const val CHANNEL = "caption-start"
        const val ACTION = "com.kigtts.next.action.START_FLOATING_CAPTIONS"
    }
}
