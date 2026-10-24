package com.kigtts.next.overlay

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.MainActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlayStateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = OverlayStore.get(context)
    @Before fun reset() { store.update { OverlaySettings() } }
    @Test fun smallWindowMasterSwitchOverridesOldIndividualPreferences() {
        val legacy = OverlaySettings(shortcuts = listOf(OverlayShortcut("camera", "拍照", "photo_camera", smallWindow = true)))
        assertTrue(legacy.preferSmallWindows)
        store.update { legacy.copy(preferSmallWindows = false) }
        assertFalse(store.settings.value.preferSmallWindows)
        assertTrue(store.settings.value.shortcuts.single().smallWindow)
        assertTrue(context.getSharedPreferences("native-overlay", 0).getString("settings", "")!!.contains("\"preferSmallWindows\":false"))
    }
    @Test fun nativeOverlayStartsOffAndIdleRetractionStartsOn() {
        assertFalse(store.settings.value.enabled); assertTrue(store.settings.value.autoHide)
        assertTrue(store.settings.value.preferSmallWindows)
        assertEquals(4, store.settings.value.shortcuts.size)
    }
    @Test fun reorderingIsStableAndDoesNotLoseIndividualWindowPreferences() {
        store.update { it.copy(shortcuts = it.shortcuts.map { item -> item.copy(smallWindow = item.id == "camera") }) }
        store.move("camera", 1); store.move("home", -99); store.move("missing", 1)
        assertEquals(listOf("home", "cards", "camera", "sketch"), store.settings.value.shortcuts.map { it.id })
        assertTrue(store.settings.value.shortcuts.single { it.id == "camera" }.smallWindow)
        assertTrue(context.getSharedPreferences("native-overlay", 0).getString("settings", "")!!.contains("smallWindow"))
    }
    @Test fun registeringAndRequestingNeverAddsUntilUserApproves() {
        val hub = OverlayHub.get(context)
        val item = OverlayShortcut("plugin:test:entry", "插件入口", "extension", owner = "test", publisher = "pub", action = "open")
        hub.register(item); assertTrue(hub.requestAdd(item)); assertTrue(hub.requestAdd(item))
        assertEquals(1, hub.requests.value.count { it.id == item.id })
        assertFalse(store.settings.value.shortcuts.any { it.id == item.id })
        hub.answer(item.id, true); assertEquals(item, store.settings.value.shortcuts.last())
        assertFalse(hub.requestAdd(item)); hub.removeOwner("test")
        assertTrue(hub.candidates.value.none { it.owner == "test" })
    }
    @Test fun rejectAndPluginRemovalClearPendingRequestsWithoutAdding() {
        val hub = OverlayHub.get(context)
        val item = OverlayShortcut("plugin:reject:x", "入口", "extension", owner = "reject", action = "open")
        hub.register(item); hub.requestAdd(item); hub.answer(item.id, false)
        assertTrue(hub.requests.value.none { it.id == item.id }); assertEquals(4, store.settings.value.shortcuts.size)
        hub.requestAdd(item); hub.removeOwner("reject"); assertTrue(hub.requests.value.none { it.id == item.id })
    }
    @Test fun exportedActivityCannotForgeAnOverlayActionAndHostRoutesAreOneUse() {
        val forged = Intent(context, MainActivity::class.java).putExtra("com.kigtts.next.OVERLAY_ROUTE", "cards")
        assertNull(OverlayLaunch.consume(forged))
        val item = defaultOverlayShortcuts()[1]; val real = OverlayLaunch.intent(context, item)
        assertEquals(item, OverlayLaunch.consume(real)); assertNull(OverlayLaunch.consume(real))
    }
    @Test fun safeBoundsKeepEveryWindowInsideCutoutsAcrossRotationAndEdgePositions() {
        for (safe in listOf(OverlayBounds(0, 80, 1080, 2200), OverlayBounds(100, 40, 2150, 950), OverlayBounds(20, 30, 180, 160)))
            for (right in listOf(false, true)) for (position in listOf(0f, .5f, 1f)) for (expanded in listOf(false, true)) {
                val p = overlayPlacement(safe, 3f, right, position, expanded)
                assertTrue(p.x >= safe.left && p.y >= safe.top)
                assertTrue(p.x + p.width <= safe.left + safe.width); assertTrue(p.y + p.height <= safe.top + safe.height)
                assertTrue(p.fabX in 0f..p.width.toFloat()); assertTrue(p.fabY in 0f..p.height.toFloat())
            }
    }
    @Test fun expandCollapseKeepsAbsoluteAnchorAtEveryHeightAndEdge() {
        val safe = OverlayBounds(0, 138, 1440, 2960)
        for (right in listOf(false, true)) for (position in listOf(0f, .1f, .5f, .9f, 1f)) {
            val compact = overlayPlacement(safe, 3.6f, right, position, false)
            val expanded = overlayPlacement(safe, 3.6f, right, position, true)
            assertEquals(compact.x + compact.fabX, expanded.x + expanded.fabX, .01f)
            assertEquals(compact.y + compact.fabY, expanded.y + expanded.fabY, .5f)
        }
    }
    @Test fun idleRetractionMovesAndClipsTheInputWindowInsteadOfLeavingAnInvisibleBlocker() {
        val safe = OverlayBounds(0, 80, 1080, 2200)
        for (right in listOf(false, true)) {
            val shown = overlayPlacement(safe, 3f, right, .5f, false)
            assertEquals(168, shown.width); assertEquals(168, shown.height)
            val hidden = overlayPlacement(safe, 3f, right, .5f, false, 1f)
            val opening = overlayPlacement(safe, 3f, right, .5f, true, 1f)
            assertEquals(hidden.x + hidden.fabX, opening.x + opening.fabX, .1f)
            assertEquals(84, hidden.width); assertEquals(shown.y, hidden.y)
            assertEquals(if (right) 1080 else 0, hidden.x + hidden.fabX.toInt())
            val oldCenter = shown.x + shown.fabX
            assertFalse(oldCenter >= hidden.x && oldCenter < hidden.x + hidden.width)
            for (frame in 0..20) {
                val p = overlayPlacement(safe, 3f, right, .5f, false, frame / 20f)
                assertTrue(p.width in 84..168)
                assertEquals(84f, p.fabY, .1f)
            }
        }
    }
    @Test fun wheelHasEndpointsAndResistsOverscrollWithoutReordering() {
        val start = dragOverlayWheel(OverlayWheelPosition(), 400f, 8, 48f)
        assertEquals(0, start.first)
        assertTrue(overlayWheelStretch(start, 8, 48f) in 0f..32f)
        val end = dragOverlayWheel(OverlayWheelPosition(), -600f, 8, 48f)
        assertEquals(3, end.first)
        assertTrue(overlayWheelStretch(end, 8, 48f) in -32f..0f)
        assertEquals(0, dragOverlayWheel(OverlayWheelPosition(), -600f, 5, 48f).first)
        assertEquals(0f, overlayWheelStretch(end.copy(drag = 0f), 8, 48f), .01f)
        assertEquals(1, settleOverlayWheel(OverlayWheelPosition(drag = -30f), 8, 48f).first)
        assertEquals(3, settleOverlayWheel(end, 8, 48f).first)
        repeat(5) { index -> val a = dialPoint(index, true, 136f); val b = dialPoint(index, false, 136f)
            assertEquals(-a.first, b.first, .01f); assertEquals(a.second, b.second, .01f) }
    }
    @Test fun onlyInternalPagesKeepTheForegroundFullscreenHost() {
        assertFalse(overlayRequestsSmallWindow(true, true, true))
        assertTrue(overlayRequestsSmallWindow(true, false, true))
        assertTrue(overlayRequestsSmallWindow(true, true, false))
        assertFalse(overlayRequestsSmallWindow(false, false, false))
    }
}
