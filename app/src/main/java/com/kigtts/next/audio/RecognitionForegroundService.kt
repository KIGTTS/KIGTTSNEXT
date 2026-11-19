package com.kigtts.next.audio

import android.app.*
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.kigtts.next.MainActivity
import com.kigtts.next.R
import com.kigtts.next.i18n.uiCopy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal interface CaptionMicrophone {
    fun prepare(): Deferred<Boolean>
    fun release()
}

/** Keeps the caption window ready for the microphone while no activity is visible. */
class RecognitionForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent?.let { IntentCompat.getParcelableExtra(it, RESULT, ResultReceiver::class.java) }
        val ready = try {
            promote(intent?.getBooleanExtra(LISTENING, false) == true)
            running = true
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to promote the caption microphone service", error)
            false
        }
        receiver?.send(if (ready) READY else FAILED, null)
        if (!ready) stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun promote(listening: Boolean) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, uiCopy("listening.notification_channel"), NotificationManager.IMPORTANCE_LOW)
        )
        val notification = notification(this, listening)
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, notification)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { running = false; super.onDestroy() }

    /** Request while the activity is visible, then wait for promotion before recording. */
    internal class Microphone(private val context: Context) : CaptionMicrophone {
        private val service = Intent(context, RecognitionForegroundService::class.java)
        private var activation: CompletableDeferred<Boolean>? = null
        private var windowOpen = false
        private var listening = false

        val permitted: Boolean get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val prepared: Boolean get() = activation?.let { !it.isCompleted || running } == true

        fun window(open: Boolean, hostVisible: Boolean) {
            windowOpen = open
            if (open && hostVisible && permitted && !prepared) request()
            stopIfUnused()
        }

        override fun prepare(): Deferred<Boolean> {
            listening = true
            if (!prepared) return request()
            updateNotification()
            return requireNotNull(activation)
        }

        private fun request(): Deferred<Boolean> {
            val request = CompletableDeferred<Boolean>()
            activation = request
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    request.complete(resultCode == READY)
                    // If closed during startup, let Android finish foreground promotion first.
                    if (activation == null) context.stopService(service) else updateNotification()
                }
            }
            try {
                ContextCompat.startForegroundService(context, Intent(service).putExtra(RESULT, receiver).putExtra(LISTENING, listening))
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to start the caption microphone service", error)
                request.complete(false)
            }
            return request
        }

        override fun release() {
            listening = false
            stopIfUnused()
            updateNotification()
        }

        private fun stopIfUnused() {
            if (listening || windowOpen) return
            val request = activation ?: return
            activation = null
            if (request.isCompleted) context.stopService(service)
        }

        private fun updateNotification() {
            if (activation?.isCompleted != true || !running) return
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(context, listening))
        }
    }

    companion object {
        const val CHANNEL = "recognition"
        private const val TAG = "CaptionMicrophone"
        private const val NOTIFICATION_ID = 104
        private const val LISTENING = "listening"
        private const val RESULT = "foreground_result"
        private const val READY = 1
        private const val FAILED = 0
        private var running = false

        private fun notification(context: Context, listening: Boolean): Notification {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_listening)
                .setContentTitle(context.uiCopy(if (listening) "listening.awaiting" else "listening.window_group"))
                .setContentText(context.uiCopy("listening.notification_return"))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        }
    }
}
