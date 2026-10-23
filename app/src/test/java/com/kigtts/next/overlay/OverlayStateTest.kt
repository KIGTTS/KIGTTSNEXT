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
        assertEquals(5, store.settings.value.shortcuts.size)
    }
    @Test fun reorderingIsStableAndDoesNotLoseIndividualWindowPreferences() {
        store.update { it.copy(shortcuts = it.shortcuts.map { item -> item.copy(smallWindow = item.id == "camera") }) }
        store.move("camera", 1); store.move("home", -99); store.move("missing", 1)
        assertEquals(listOf("home", "cards", "camera", "sketch", "quick-text"), store.settings.value.shortcuts.map { it.id })
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
        assertTrue(hub.requests.value.none { it.id == item.id }); assertEquals(5, store.settings.value.shortcuts.size)
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
    @Test fun wheelWrapsAndMirrorsInsteadOfChangingOrderOnOtherEdge() {
        assertEquals(4, wrappedIndex(-1, 5)); assertEquals(1, wrappedIndex(6, 5)); assertEquals(0, wrappedIndex(1, 0))
        repeat(5) { index -> val a = dialPoint(index, true, 136f); val b = dialPoint(index, false, 136f)
            assertEquals(-a.first, b.first, .01f); assertEquals(a.second, b.second, .01f) }
    }
}
