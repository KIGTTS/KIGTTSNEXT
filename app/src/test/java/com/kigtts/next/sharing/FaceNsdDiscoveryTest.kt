package com.kigtts.next.sharing

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ClassName
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetwork
import java.net.InetAddress
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [FaceNsdDiscoveryTest.Nsd::class])
class FaceNsdDiscoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val nsd = Shadow.extract<Nsd>(context.getSystemService(NsdManager::class.java))
    private val self = FaceLanIdentity(faceToken(), "我的手机", 12345, faceToken())
    private val found = mutableListOf<FacePeer>()
    private val lost = mutableListOf<String>()
    private val discovery = FaceNsdDiscovery(context, found::add, lost::add)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun service(id: String = faceToken(), address: String = "192.168.8.2") = NsdServiceInfo().apply {
        serviceName = "KIGTTS-${id.take(10)}"; serviceType = FaceLanDiscovery.TYPE; port = 23456
        @Suppress("DEPRECATION")
        host = InetAddress.getByName(address)
        setAttribute("id", id); setAttribute("key", faceToken()); setAttribute("name", "对方")
    }

    @Test fun bothSidesAdvertiseAndRegistrationAndBrowseFailuresRetry() {
        discovery.start(self)
        val advertised = nsd.registrations.single().first
        assertEquals(self.token, advertised.attributes.getValue("key").toString(Charsets.UTF_8))
        nsd.registrations.single().second.onRegistrationFailed(advertised, NsdManager.FAILURE_INTERNAL_ERROR)
        nsd.browses.single().onStartDiscoveryFailed(FaceLanDiscovery.TYPE, NsdManager.FAILURE_INTERNAL_ERROR)
        idle(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(2, nsd.registrations.size); assertEquals(2, nsd.browses.size)
        discovery.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(2, nsd.registrations.size); assertEquals(2, nsd.browses.size)
    }

    @Test fun resolutionsAreSerialRetryAndDoNotBlockOtherPhones() {
        discovery.start(self)
        val first = service(); val second = service()
        val browse = nsd.browses.single()
        browse.onServiceFound(first); browse.onServiceFound(second); browse.onServiceFound(first); idle()
        assertEquals(1, nsd.resolutions.size)
        nsd.resolutions[0].second.onResolveFailed(first, NsdManager.FAILURE_ALREADY_ACTIVE); idle()
        assertEquals(2, nsd.resolutions.size)
        nsd.resolutions[1].second.onServiceResolved(second); idle()
        assertEquals(listOf("192.168.8.2"), found.map { it.host })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(3, nsd.resolutions.size)
        nsd.resolutions[2].second.onServiceResolved(first); idle()
        assertEquals(2, found.size)
        browse.onServiceLost(second); idle(); assertEquals(listOf(found[0].id), lost)
        discovery.stop()
    }

    @Test fun resolveTimeoutAdvancesQueueAndOldCallbacksCannotResurrectPeers() {
        discovery.start(self)
        val first = service(); val second = service()
        val browse = nsd.browses.single()
        browse.onServiceFound(first); browse.onServiceFound(second); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        assertEquals(2, nsd.resolutions.size)
        nsd.resolutions[0].second.onServiceResolved(first); idle(); assertTrue(found.isEmpty())
        discovery.stop()
        nsd.resolutions[1].second.onServiceResolved(second)
        browse.onServiceFound(first); browse.onServiceLost(second); idle()
        assertTrue(found.isEmpty()); assertTrue(lost.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(2, nsd.resolutions.size)
    }

    @Test fun vanishedServiceCannotReappearFromAnInFlightResolution() {
        discovery.start(self)
        val info = service(); val browse = nsd.browses.single()
        browse.onServiceFound(info); idle()
        browse.onServiceLost(info); idle()
        nsd.resolutions.single().second.onServiceResolved(info); idle()
        assertTrue(found.isEmpty()); discovery.stop()
    }

    @Test fun endpointValidationRejectsPublicAddressesBadTokensAndOwnIdentity() {
        assertNull(faceNsdPeer(service(address = "8.8.8.8"), self.id))
        assertNull(faceNsdPeer(service(self.id), self.id))
        assertNull(faceNsdPeer(service().apply { setAttribute("key", "bad") }, self.id))
        assertNull(faceNsdPeer(service().apply { port = 0 }, self.id))
        assertEquals("192.168.8.2", faceNsdPeer(service(), self.id)?.host)
    }

    @Test fun ipv4IsPreferredOverLinkLocalIpv6OnModernNsd() {
        val info = service().apply { hostAddresses = listOf(InetAddress.getByName("fe80::1234"), InetAddress.getByName("192.168.8.3")) }
        assertEquals("192.168.8.3", faceNsdPeer(info, self.id)?.host)
    }

    @Test @Config(sdk = [28]) fun olderAndroidUsesTheLegacyResolveAddress() {
        assertEquals("192.168.8.2", faceNsdPeer(service(), self.id)?.host)
        discovery.start(self); nsd.browses.single().onServiceFound(service()); idle()
        assertEquals(1, nsd.resolutions.size); discovery.stop()
    }

    @Test fun repeatedStartDoesNotReRegisterOrLeaveRetriesRunningAfterStop() {
        val lan = FaceLanDiscovery(context, found::add, lost::add)
        try {
            lan.start(self.id, self.name, self.port, self.token)
            lan.start(self.id, self.name, self.port, self.token)
            assertEquals(1, nsd.registrations.size); assertEquals(1, nsd.browses.size)
        } finally { lan.stop() }
        assertEquals(1, nsd.unregistered); assertEquals(1, nsd.stopped)
    }

    @Test fun wifiChangesRestartOnceAndClosingReleasesMulticastAndNetworkWatcher() {
        val networks = shadowOf(context.getSystemService(ConnectivityManager::class.java))
        val wifi = shadowOf(context.getSystemService(WifiManager::class.java))
        val before = networks.networkCallbacks.toSet(); val locks = wifi.activeLockCount
        val lan = FaceLanDiscovery(context, found::add, lost::add)
        try {
            lan.start(self.id, self.name, self.port, self.token)
            assertEquals(locks + 1, wifi.activeLockCount)
            val callback = (networks.networkCallbacks - before).single()
            val network = ShadowNetwork.newInstance(10)
            callback.onAvailable(network); callback.onLinkPropertiesChanged(network, LinkProperties()); idle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750))
            assertEquals(2, nsd.registrations.size); assertEquals(locks + 1, wifi.activeLockCount)
            lan.stop()
            assertEquals(locks, wifi.activeLockCount); assertEquals(before, networks.networkCallbacks)
            callback.onLost(network); idle(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertEquals(2, nsd.registrations.size)
        } finally { lan.stop() }
    }

    @Implements(NsdManager::class)
    class Nsd {
        val registrations = mutableListOf<Pair<NsdServiceInfo, NsdManager.RegistrationListener>>()
        val browses = mutableListOf<NsdManager.DiscoveryListener>()
        val resolutions = mutableListOf<Pair<NsdServiceInfo, NsdManager.ResolveListener>>()
        var unregistered = 0; var stopped = 0
        @Implementation(maxSdk = 28) fun __constructor__(context: Context, @ClassName("android.net.nsd.INsdManager") service: Any) = Unit
        @Implementation fun registerService(info: NsdServiceInfo, protocol: Int, listener: NsdManager.RegistrationListener) { registrations += info to listener }
        @Implementation fun unregisterService(listener: NsdManager.RegistrationListener) { unregistered++ }
        @Implementation fun discoverServices(type: String, protocol: Int, listener: NsdManager.DiscoveryListener) { browses += listener }
        @Implementation fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) { stopped++ }
        @Implementation fun resolveService(info: NsdServiceInfo, listener: NsdManager.ResolveListener) { resolutions += info to listener }
        @Implementation fun stopServiceResolution(listener: NsdManager.ResolveListener) = Unit
    }
}
