package com.kigtts.next.overlay

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class OverlayServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @After fun reset() { OverlayStore.get(app).update { OverlaySettings() }; ShadowSettings.setCanDrawOverlays(false) }
    @Test fun deniedPermissionDoesNotStartAServiceOrShowAWindow() {
        ShadowSettings.setCanDrawOverlays(false); OverlayStore.get(app).update { it.copy(enabled = true) }
        assertFalse(NativeOverlayService.sync(app)); assertNull(shadowOf(app).nextStartedService)
    }
    @Test fun windowUsesHardwareAccelerationAndDoesNotApplySystemInsetsTwice() {
        val params = overlayWindowParams()
        assertEquals(0, params.fitInsetsTypes)
        assertEquals(android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, params.layoutInDisplayCutoutMode)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals(80, params.width); assertEquals(80, params.height)
    }
    @Test fun enabledServiceOwnsItsCompositionAndCanDestroyRecreateAfterRotation() {
        ShadowSettings.setCanDrawOverlays(true); OverlayStore.get(app).update { it.copy(enabled = true) }
        val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
        val service = controller.get()
        assertNotNull(shadowOf(service).lastForegroundNotification)
        val rotated = android.content.res.Configuration(service.resources.configuration).apply { orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        service.onConfigurationChanged(rotated)
        controller.destroy()
        val recreated = Robolectric.buildService(NativeOverlayService::class.java).create()
        assertNotNull(shadowOf(recreated.get()).lastForegroundNotification); recreated.destroy()
    }
}
