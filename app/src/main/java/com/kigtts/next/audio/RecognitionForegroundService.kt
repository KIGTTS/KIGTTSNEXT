package com.kigtts.next.audio

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.kigtts.next.MainActivity
import com.kigtts.next.R
import com.kigtts.next.i18n.uiCopy

/** Keeps an explicitly started microphone session visible to Android; never starts recording on its own. */
class RecognitionForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, uiCopy("listening.notification_channel"), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_listening)
            .setContentTitle(uiCopy("listening.awaiting")).setContentText(uiCopy("listening.notification_return"))
            .setContentIntent(open).setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 30) startForeground(104, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(104, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val CHANNEL = "recognition" }
}
