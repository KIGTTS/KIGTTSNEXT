package com.kigtts.next

import android.os.Looper
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
