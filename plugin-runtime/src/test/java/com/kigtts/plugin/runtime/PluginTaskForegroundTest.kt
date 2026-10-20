package com.kigtts.plugin.runtime

import android.app.NotificationManager
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginTaskForegroundTest {
    @Test fun foregroundWorkIsVisibleOnlyWhileGenerationIsActive() {
        val controller = Robolectric.buildService(PluginWorker0::class.java).create()
        val service = controller.get(); val foreground = PluginTaskForeground(service)
        foreground.update(true, "云端助手")
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals("云端助手", shadowOf(service).lastForegroundNotification.extras.getString("android.title"))
        foreground.update(true, "云端助手")
        foreground.update(false, "云端助手")
        assertTrue(shadowOf(service).isForegroundStopped)
        foreground.close(); controller.destroy()
    }
}
