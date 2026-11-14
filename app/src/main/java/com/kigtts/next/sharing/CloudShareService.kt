package com.kigtts.next.sharing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kigtts.next.MainActivity
import com.kigtts.next.R

/** Keep the sender process available while the user moves away from the share panel. */
class CloudShareService : Service() {
    private var owner: Active? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = current
        if (active == null) { stopSelf(startId); return START_NOT_STICKY }
        if (intent?.getStringExtra(EXTRA_SHARE) != active.key) return START_NOT_STICKY
        if (intent?.action == STOP) {
            active.stop()
            return START_NOT_STICKY
        }
        owner = active
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "云共享", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SHARE, active.key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, CloudShareService::class.java)
            .setAction(STOP).putExtra(EXTRA_SHARE, active.key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_face_share)
            .setContentTitle("云共享进行中").setContentText("链接可以重复打开，点击返回分享")
            .setOngoing(true).setContentIntent(open).addAction(0, "结束分享", stop).build()
        try { startForeground(4804, notification) } catch (_: Exception) {
            if (current === active) current = null
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val active = owner
        if (active != null && current === active) { current = null; active.stop() }
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_SHARE = "com.kigtts.next.cloud_share_session"
        private const val STOP = "com.kigtts.next.stop_cloud_share"
        private const val CHANNEL = "cloud-share"
        private class Active(val key: String, val stop: () -> Unit)
        private var current: Active? = null

        internal fun start(context: Context, key: String, stop: () -> Unit) {
            val active = Active(key, stop)
            current = active
            runCatching {
                context.startForegroundService(Intent(context, CloudShareService::class.java).putExtra(EXTRA_SHARE, key))
            }.onFailure { if (current === active) current = null }
        }

        internal fun finish(context: Context, key: String) {
            if (current?.key != key) return
            current = null
            context.stopService(Intent(context, CloudShareService::class.java))
        }
    }
}
