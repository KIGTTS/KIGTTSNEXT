package com.kigtts.next.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kigtts.next.R
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.manager.HostPluginRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Keeps the existing process-wide bake alive while a batch is in progress. */
class ReadingCacheForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observation: Job? = null
    private var finished = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        requested = false
        val reading = HostPluginRuntime.get(application).reading
        if (intent?.action == CANCEL) {
            reading.cancelBackgroundBake()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val manager = getSystemService(NotificationManager::class.java)
        try {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, uiCopy("reading.cache_notification_channel"),
                NotificationManager.IMPORTANCE_LOW))
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(reading.bakeProgress?.value),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            running = true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to promote the reading cache service", error)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        observation?.cancel()
        observation = scope.launch {
            val progress = reading.bakeProgress ?: flowOf(null)
            reading.backgroundBakeActive.combine(progress, ::Pair).collect { (active, value) ->
                if (!active) {
                    finished = true
                    stopSelf(startId)
                }
                else runCatching { manager.notify(NOTIFICATION_ID, notification(value)) }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(progress: Float?): Notification {
        val cancel = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_listening)
            .setContentTitle(uiCopy("reading.cache_notification_title"))
            .setContentText(uiCopy("reading.cache_notification_body"))
            .setProgress(100, ((progress ?: 0f) * 100).roundToInt(), progress == null)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, uiCopy("reading.cache_notification_stop"), cancel).build())
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
        if (finished) HostPluginRuntime.get(application).resumeReadingCache()
    }

    companion object {
        private const val TAG = "ReadingCacheService"
        private const val CHANNEL = "reading-cache"
        private const val NOTIFICATION_ID = 105
        private const val CANCEL = "com.kigtts.next.audio.CANCEL_READING_CACHE"
        private var requested = false
        private var running = false

        internal fun start(context: Context) {
            if (running || requested) return
            requested = true
            try {
                ContextCompat.startForegroundService(context, Intent(context, ReadingCacheForegroundService::class.java))
            } catch (error: RuntimeException) {
                requested = false
                Log.w(TAG, "Unable to start the reading cache service", error)
            }
        }
    }
}
