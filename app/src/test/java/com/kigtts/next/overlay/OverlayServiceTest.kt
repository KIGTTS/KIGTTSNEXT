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
    @Test fun idleHidesByDefaultAndKeepsTheNarrowTouchWindow() {
        ShadowSettings.setCanDrawOverlays(true); OverlayStore.get(app).update { it.copy(enabled = true) }
        val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
        try {
            val fullWidth = placement(controller.get()).width
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
            assertTrue(hidden(controller.get()))
            assertTrue("Idle hiding must shrink the native touch window, even without a Compose frame",
                placement(controller.get()).width <= fullWidth / 2 + 1)
        } finally { controller.destroy() }
    }
    @Test fun anExpiredOrUnavailableNoticeCannotPreventIdleHideForever() {
        ShadowSettings.setCanDrawOverlays(true); OverlayStore.get(app).update { it.copy(enabled = true) }
        OverlayHub.get(app).show(OverlayNotice("unavailable.plugin", "missing", "测试", null, "info", null), 2000)
        val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
        try {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(8))
            assertNull(OverlayHub.get(app).bubble.value)
            assertTrue("Idle hiding must resume when the notice disappears", hidden(controller.get()))
        } finally { controller.destroy() }
    }
    @Test @Config(sdk = [29, 35])
    fun idleRetractionWakesOnBothEdgesAndHonorsTheAutoHideSetting() {
        ShadowSettings.setCanDrawOverlays(true)
        val main = shadowOf(android.os.Looper.getMainLooper())
        for (right in listOf(true, false)) {
            OverlayStore.get(app).update { it.copy(enabled = true, autoHide = true, rightEdge = right) }
            val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
            try {
                val service = controller.get()
                val full = placement(service).width
                main.idleFor(java.time.Duration.ofSeconds(5))
                assertTrue(placement(service).width <= full / 2 + 1)
                invoke(service, "interact")
                main.idleFor(java.time.Duration.ofMillis(350))
                assertEquals(full, placement(service).width)
                OverlayStore.get(app).update { it.copy(autoHide = false) }
                main.idleFor(java.time.Duration.ofSeconds(5))
                assertFalse(hidden(service))
                assertEquals(full, placement(service).width)
                OverlayStore.get(app).update { it.copy(autoHide = true) }
                main.idleFor(java.time.Duration.ofSeconds(5))
                assertTrue(hidden(service))
                assertTrue(placement(service).width <= full / 2 + 1)
            } finally { controller.destroy() }
        }
    }
    private fun hidden(service: NativeOverlayService): Boolean {
        val field = NativeOverlayService::class.java.getDeclaredField("hidden\$delegate").apply { isAccessible = true }
        return (field.get(service) as androidx.compose.runtime.State<*>).value == true
    }
    @Test fun deniedPermissionDoesNotStartAServiceOrShowAWindow() {
        ShadowSettings.setCanDrawOverlays(false); OverlayStore.get(app).update { it.copy(enabled = true) }
        assertFalse(NativeOverlayService.sync(app)); assertNull(shadowOf(app).nextStartedService)
    }
    @Test fun expandedServiceRejectsBallDraggingAndKeepsItsAnchor() {
        ShadowSettings.setCanDrawOverlays(true); OverlayStore.get(app).update { it.copy(enabled = true) }
        val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
        try {
            val service = controller.get()
            invoke(service, "toggle")
            val before = placement(service)
            invoke(service, "drag", -900f, -9000f)
            invoke(service, "drop")
            assertEquals(before, placement(service))
            assertTrue(state(service, "expanded"))
        } finally { controller.destroy() }
    }
    @Test @Config(sdk = [29, 35])
    fun openingTheAppCannotShrinkTheWindowBeforeTheFlowerFinishesClosing() {
        ShadowSettings.setCanDrawOverlays(true); OverlayStore.get(app).update { it.copy(enabled = true) }
        val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
        try {
            val service = controller.get()
            invoke(service, "toggle")
            val expanded = placement(service)
            invoke(service, "collapse")
            service.onConfigurationChanged(android.content.res.Configuration(service.resources.configuration))
            assertEquals("Configuration refresh must retain the animation surface", expanded.width, placement(service).width)
            assertEquals(expanded.height, placement(service).height)
            invoke(service, "finishCollapse")
            assertTrue(placement(service).width < expanded.width)
        } finally { controller.destroy() }
    }
    @Test fun interruptedDockingCanBeDraggedAndReleasedAgainWithoutGettingStuck() {
        ShadowSettings.setCanDrawOverlays(true); OverlayStore.get(app).update { it.copy(enabled = true) }
        val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
        try {
            val service = controller.get()
            invoke(service, "drag", -200f, -9000f)
            val top = placement(service)
            val safe = NativeOverlayService::class.java.getDeclaredMethod("safeBounds").apply { isAccessible = true }.invoke(service) as OverlayBounds
            assertEquals(overlayAnchorRange(safe, service.resources.displayMetrics.density).start, top.centerOnScreen().y, .5f)
            invoke(service, "drop")
            assertTrue(state(service, "docking"))
            invoke(service, "drag", 100f, 120f)
            assertFalse(state(service, "docking"))
            invoke(service, "drop")
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
            assertFalse(state(service, "docking"))
            assertTrue(hidden(service))
        } finally { controller.destroy() }
    }

    @Test fun pushingEitherEdgeDoesNotAccumulateDistanceOrShrinkTheDragWindow() {
        ShadowSettings.setCanDrawOverlays(true)
        val main = shadowOf(android.os.Looper.getMainLooper())
        for (right in listOf(false, true)) {
            OverlayStore.get(app).update { it.copy(enabled = true, autoHide = true, rightEdge = right) }
            val controller = Robolectric.buildService(NativeOverlayService::class.java).create()
            try {
                val service = controller.get()
                main.idleFor(java.time.Duration.ofSeconds(5))
                val hidden = placement(service)
                val outward = if (right) 1f else -1f
                invoke(service, "drag", outward * 3000f, 0f)
                val atEdge = placement(service)
                assertTrue(atEdge.width > hidden.width)
                assertEquals(atEdge.height, atEdge.width)
                if (right) assertEquals(hidden.x + hidden.width, atEdge.x + atEdge.width)
                else assertEquals(hidden.x, atEdge.x)
                repeat(10) { invoke(service, "drag", outward * 100f, 0f) }
                assertEquals(atEdge, placement(service))
                invoke(service, "drag", -outward * 4f, 0f)
                assertEquals(atEdge.copy(x = atEdge.x - outward.toInt() * 4), placement(service))
            } finally { controller.destroy() }
        }
    }
    private fun placement(service: NativeOverlayService) = NativeOverlayService::class.java.getDeclaredField("layout").apply { isAccessible = true }.get(service) as OverlayPlacement
    private fun state(service: NativeOverlayService, name: String) = (NativeOverlayService::class.java.getDeclaredField("${name}\$delegate").apply { isAccessible = true }.get(service) as androidx.compose.runtime.State<*>).value == true
    private fun invoke(service: NativeOverlayService, name: String, vararg values: Float) {
        NativeOverlayService::class.java.getDeclaredMethod(name, *Array(values.size) { Float::class.javaPrimitiveType!! })
            .apply { isAccessible = true }.invoke(service, *values.toTypedArray())
    }
    @Test fun windowUsesHardwareAccelerationAndDoesNotApplySystemInsetsTwice() {
        val params = overlayWindowParams()
        assertEquals(0, params.fitInsetsTypes)
        assertEquals(android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, params.layoutInDisplayCutoutMode)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals(80, params.width); assertEquals(80, params.height)
        assertEquals(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, params.softInputMode)
    }
    @Test fun constrainedWindowCannotSuspendRenderingForever() {
        val gate = OverlayResizeGate()
        gate.resizing()
        repeat(3) { assertFalse(gate.canDraw(false)) }
        repeat(20) { assertTrue(gate.canDraw(false)) }
        gate.resizing(); assertTrue(gate.canDraw(true)); assertTrue(gate.canDraw(false))
    }
    @Test fun smallWindowRequestsFreeformModeInsteadOfOnlyBounds() {
        val options = OverlaySmallWindow.options(app)
        assertEquals(5, options.getInt("android.activity.windowingMode"))
        val bounds = options.getParcelable<android.graphics.Rect>("android:activity.launchBounds")
        assertNull(bounds)
        // System freeform policy owns geometry; no custom aspect or DPI override.
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
