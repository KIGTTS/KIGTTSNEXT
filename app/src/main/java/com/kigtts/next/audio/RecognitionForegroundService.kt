package com.kigtts.next.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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

/** Keeps an explicitly started microphone session visible to Android; never starts recording on its own. */
class RecognitionForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent?.let { IntentCompat.getParcelableExtra(it, RESULT, ResultReceiver::class.java) }
        val ready = try {
            promote()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to promote the caption microphone service", error)
            false
        }
        receiver?.send(if (ready) READY else FAILED, null)
        if (!ready) stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun promote() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, uiCopy("listening.notification_channel"), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_listening)
            .setContentTitle(uiCopy("listening.awaiting")).setContentText(uiCopy("listening.notification_return"))
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(104, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(104, notification)
    }
    override fun onBind(intent: Intent?): IBinder? = null

    /** Request while the activity is visible, then wait for promotion before recording. */
    internal class Microphone(private val context: Context) : CaptionMicrophone {
        private val service = Intent(context, RecognitionForegroundService::class.java)
        private var activation: CompletableDeferred<Boolean>? = null

        override fun prepare(): Deferred<Boolean> {
            activation?.let { return it }
            val request = CompletableDeferred<Boolean>()
            activation = request
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    request.complete(resultCode == READY)
                    // If closed during startup, let Android finish foreground promotion first.
                    if (activation == null) context.stopService(service)
                }
            }
            try {
                ContextCompat.startForegroundService(context, Intent(service).putExtra(RESULT, receiver))
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to start the caption microphone service", error)
                request.complete(false)
            }
            return request
        }

        override fun release() {
            val request = activation ?: return
            activation = null
            if (request.isCompleted) context.stopService(service)
        }
    }

    companion object {
        const val CHANNEL = "recognition"
        private const val TAG = "CaptionMicrophone"
        private const val RESULT = "foreground_result"
        private const val READY = 1
        private const val FAILED = 0
    }
}
