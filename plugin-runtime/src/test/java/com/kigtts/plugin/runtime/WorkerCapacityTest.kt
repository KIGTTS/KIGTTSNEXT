package com.kigtts.plugin.runtime

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class WorkerCapacityTest {
    @Test fun everySlotHasItsOwnPrivateManifestService() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(100, workerServices.size)
        assertEquals(workerServices.size, workerServices.distinct().size)
        workerServices.forEachIndexed { index, service ->
            val info = context.packageManager.getServiceInfo(ComponentName(context, service), 0)
            assertEquals("${context.packageName}:plugin$index", info.processName)
            assertFalse(info.exported)
            assertTrue(PluginWorkerService::class.java.isAssignableFrom(service))
        }
    }
}
