package com.kigtts.next.sharing

import android.content.Context
import android.net.wifi.*
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], shadows = [FaceHotspotTest.Wifi::class, FaceHotspotTest.Reservation::class, FaceHotspotTest.HotspotConfig::class])
class FaceHotspotTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val wifi = Shadow.extract<Wifi>(context.getSystemService(WifiManager::class.java))

    @Test fun hotspotCanStartWithoutABrowserAndLateCallbacksCannotResurrectIt() {
        val errors = mutableListOf<String>(); val hotspot = FaceHotspot(context, errors::add)
        hotspot.start(); assertTrue(hotspot.starting.value)
        val callback = wifi.callback!!
        val reservation = Shadow.newInstanceOf(WifiManager.LocalOnlyHotspotReservation::class.java)
        callback.onStarted(reservation)
        assertFalse(hotspot.starting.value); assertEquals("KIGTTS", hotspot.details.value?.ssid)
        assertTrue(hotspot.details.value!!.qr().startsWith("WIFI:"))
        hotspot.stop(); assertNull(hotspot.details.value)
        assertTrue(Shadow.extract<Reservation>(reservation).closed)
        hotspot.start(); val stale = wifi.callback!!; hotspot.stop()
        val late = Shadow.newInstanceOf(WifiManager.LocalOnlyHotspotReservation::class.java)
        stale.onStarted(late)
        assertTrue(Shadow.extract<Reservation>(late).closed); assertNull(hotspot.details.value)
        assertTrue(errors.isEmpty())
    }
    @Test fun creationTimeoutReleasesThePendingStateAndAllowsRetry() {
        val errors = mutableListOf<String>(); val hotspot = FaceHotspot(context, errors::add)
        hotspot.start(); val stale = wifi.callback!!
        shadowOf(Looper.getMainLooper()).idleFor(21, TimeUnit.SECONDS)
        assertFalse(hotspot.starting.value); assertEquals(1, errors.size)
        hotspot.start(); assertTrue(hotspot.starting.value)
        stale.onFailed(0); assertTrue(hotspot.starting.value)
        wifi.callback!!.onFailed(0); assertFalse(hotspot.starting.value)
        hotspot.stop()
    }
    @Implements(WifiManager::class) class Wifi {
        var callback: WifiManager.LocalOnlyHotspotCallback? = null
        @Implementation fun startLocalOnlyHotspot(value: WifiManager.LocalOnlyHotspotCallback, handler: Handler?) { callback = value }
    }
    @Implements(WifiManager.LocalOnlyHotspotReservation::class) class Reservation {
        var closed = false
        @Implementation fun getSoftApConfiguration() = Shadow.newInstanceOf(SoftApConfiguration::class.java)
        @Implementation fun close() { closed = true }
    }
    @Implements(SoftApConfiguration::class) class HotspotConfig {
        @Implementation fun getSsid() = "KIGTTS"
        @Implementation fun getPassphrase() = "12345678"
    }
}
