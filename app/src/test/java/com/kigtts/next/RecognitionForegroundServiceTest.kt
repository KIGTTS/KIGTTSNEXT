package com.kigtts.next

import android.Manifest
import android.app.NotificationManager
import android.os.Looper
import com.kigtts.next.audio.CaptionStartNotification
import com.kigtts.next.audio.RecognitionForegroundService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecognitionForegroundServiceTest {
    @Test fun openCaptionWindowKeepsTheServiceAvailableAcrossStartAndStop() {
        val application = RuntimeEnvironment.getApplication()
        val app = shadowOf(application)
        app.grantPermissions(Manifest.permission.RECORD_AUDIO)
        val client = RecognitionForegroundService.Microphone(application)
        client.window(open = true, hostVisible = true)
        val launch = requireNotNull(app.nextStartedService)
        assertFalse(launch.getBooleanExtra("listening", true))

        val service = Robolectric.buildService(RecognitionForegroundService::class.java, launch).create()
        try {
            service.get().onStartCommand(launch, 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(client.prepared)
            assertTrue(runBlocking { client.prepare().await() })
            assertNull(app.nextStartedService)
            client.release()
            assertTrue(client.prepared)
            assertNull(app.nextStoppedService)

            client.window(open = false, hostVisible = false)
            assertFalse(client.prepared)
            assertEquals(launch.component, app.nextStoppedService?.component)
        } finally {
            service.destroy()
        }
    }

    @Test fun openingInTheBackgroundWaitsForExplicitNotificationConfirmation() {
        val application = RuntimeEnvironment.getApplication()
        val app = shadowOf(application)
        app.grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        val client = RecognitionForegroundService.Microphone(application)
        client.window(open = true, hostVisible = false)
        assertFalse(client.prepared)
        assertNull(app.nextStartedService)

        var confirmed = false
        val confirmation = CaptionStartNotification(application, { it }) { confirmed = true }
        try {
            assertTrue(confirmation.show())
            assertFalse(confirmed)
            val notifications = shadowOf(application.getSystemService(NotificationManager::class.java))
            notifications.allNotifications.single().contentIntent.send()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(confirmed)
            assertNull(app.nextStartedActivity)
        } finally {
            confirmation.clear()
        }
    }

    @Test fun closingDuringStartupWaitsUntilTheServiceHasPromoted() {
        val application = RuntimeEnvironment.getApplication()
        val app = shadowOf(application)
        val client = RecognitionForegroundService.Microphone(application)
        val ready = client.prepare()
        val launch = requireNotNull(app.nextStartedService)
        assertFalse(ready.isCompleted)
        client.release()
        assertNull(app.nextStoppedService)

        val service = Robolectric.buildService(RecognitionForegroundService::class.java, launch).create()
        try {
            service.get().onStartCommand(launch, 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(ready.isCompleted)
            assertTrue(runBlocking { ready.await() })
            assertNotNull(shadowOf(service.get()).lastForegroundNotification)
            assertEquals(launch.component, app.nextStoppedService?.component)
        } finally {
            service.destroy()
        }
    }
}
