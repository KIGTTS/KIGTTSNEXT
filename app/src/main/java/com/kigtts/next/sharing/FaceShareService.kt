package com.kigtts.next.sharing

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kigtts.next.MainActivity
import com.kigtts.next.R

/** Sharing stays explicit and can be ended from its notification without returning to the album. */
class FaceShareService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopAction?.invoke(); stopSelf(); return START_NOT_STICKY }
        if (stopAction == null) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action in setOf("accept", "reject")) {
            intent?.getStringExtra("offer")?.let { incomingAction?.invoke(it, intent.action == "accept") }
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("face-share", "面对面分享", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, FaceShareService::class.java).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "face-share").setSmallIcon(R.drawable.ic_stat_face_share)
            .setContentTitle("面对面已开启").setContentText("可以发现附近的人并接收分享")
            .setContentIntent(open).setOngoing(true).addAction(0, "结束分享", stop).build()
        try { startForeground(4802, notification) } catch (_: Exception) { stopAction?.invoke(); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { super.onDestroy(); val action = stopAction; stopAction = null; action?.invoke() }
    companion object {
        private var stopAction: (() -> Unit)? = null
        private var incomingAction: ((String, Boolean) -> Unit)? = null
        fun start(context: Context, stop: () -> Unit, incoming: (String, Boolean) -> Unit) {
            stopAction = stop
            incomingAction = incoming
            runCatching { context.startForegroundService(Intent(context, FaceShareService::class.java)) }
        }
        fun finish(context: Context) { stopAction = null; incomingAction = null; context.stopService(Intent(context, FaceShareService::class.java)) }
    }
}
