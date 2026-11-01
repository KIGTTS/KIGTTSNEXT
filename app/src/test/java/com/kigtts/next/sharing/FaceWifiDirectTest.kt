package com.kigtts.next.sharing

import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.*
import android.net.wifi.p2p.nsd.*
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [29, 35], shadows = [FaceWifiDirectTest.P2p::class])
class FaceWifiDirectTest {
    @Test fun senderCanBecomeGroupOwnerAndReuseItsConnectedRecipient() = runBlocking {
        val wifi = FaceWifiDirect(context) {}; wifi.start()
        try {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            radio.group = connection(P2p.OWN, true).apply {
                ReflectionHelpers.setField(this, "mClients", arrayListOf(WifiP2pDevice().apply { deviceAddress = remote }))
            }
            announce()
            assertNull(withTimeout(3000) { pending.await() })
            radio.requested = null
            assertNull(wifi.connect(remote))
            assertNull("已经连上时无需重复配对", radio.requested)
        } finally { wifi.stop() }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val radio = Shadow.extract<P2p>(context.getSystemService(WifiP2pManager::class.java))
    private val remote = "aa:bb:cc:dd:ee:ff"
    private fun connection(owner: String, ours: Boolean) = WifiP2pGroup().apply {
        ReflectionHelpers.setField(this, "mIsGroupOwner", ours)
        ReflectionHelpers.setField(this, "mOwner", WifiP2pDevice().apply { deviceAddress = owner })
    }
    private fun announce() { context.sendBroadcast(Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun discoveryDoesNotCreateAnAutonomousGroupAndConnectionWaitsForTheSelectedRecipient() = runBlocking {
        val wifi = FaceWifiDirect(context) {}
        wifi.start()
        try {
            assertNull(radio.group); assertEquals(0, radio.created)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            assertEquals(0, radio.removed); assertEquals(remote, radio.requested)
            radio.group = connection("11:22:33:44:55:66", false); announce()
            assertFalse(pending.isCompleted)
            radio.group = connection(remote, false); announce()
            assertEquals("192.168.49.1", withTimeout(3000) { pending.await() })
            assertEquals(P2p.OWN, wifi.address)
        } finally { wifi.stop() }
    }

    @Test fun aGroupCreatedElsewhereIsPreservedAndFailureUsesCurrentReceiveControls() = runBlocking {
        radio.group = connection(P2p.OWN, true); radio.failConnect = true
        ReflectionHelpers.setField(radio.group, "mClients", arrayListOf(WifiP2pDevice().apply { deviceAddress = "11:22:33:44:55:66" }))
        val wifi = FaceWifiDirect(context) {}; wifi.start()
        try {
            val error = runCatching { wifi.connect(remote) }.exceptionOrNull()!!
            assertEquals(0, radio.removed)
            assertTrue(error.message!!.contains("开启面对面接收"))
            assertFalse(error.message!!.contains("面对面页面"))
        } finally { wifi.stop() }
    }

    @Test fun anEmptyGroupRetainedByTheSystemIsReleasedBeforeJoiningTheRecipient() = runBlocking {
        radio.group = connection(P2p.OWN, true)
        val wifi = FaceWifiDirect(context) {}; wifi.start()
        try {
            assertEquals(0, radio.removed) // Merely opening sharing must not change an existing group.
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            assertEquals(1, radio.removed); assertEquals(remote, radio.requested)
            radio.group = connection(remote, false); announce()
            assertEquals("192.168.49.1", withTimeout(3000) { pending.await() })
        } finally { wifi.stop() }
    }

    @Test fun alreadyConnectedRecipientIsReusedAndCancellationStopsNegotiation() = runBlocking {
        val wifi = FaceWifiDirect(context) {}; wifi.start()
        try {
            radio.group = connection(remote, false)
            assertEquals("192.168.49.1", wifi.connect(remote)); assertNull(radio.requested)
            radio.group = null
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            pending.cancelAndJoin(); assertEquals(1, radio.cancelled)
        } finally { wifi.stop() }
    }

    @Test fun serviceDiscoveryUsesTheRemoteAddressWhenOurOwnAddressIsHiddenAndStopsCleanly() {
        radio.ownAddress = "02:00:00:00:00:00"
        val found = mutableListOf<FacePeer>()
        val identity = FaceLanIdentity(faceToken(), "本人", 12345, faceToken())
        val wifi = FaceWifiDirect(context, found::add) {}
        wifi.start(identity)
        try {
            assertNull(wifi.address); assertEquals(0, radio.created)
            assertEquals(1, radio.serviceDiscoveries)
            // Empty DNS-SD query requests both PTR and TXT; a service-type PTR query loses our session.
            assertEquals("02000100", radio.requestQueries.single())
            val id = faceToken(); val key = faceToken()
            val record = mapOf("id" to id, "key" to key, "port" to "54321", "name" to "对方")
            val callback = requireNotNull(radio.txt)
            val device = WifiP2pDevice().apply { deviceAddress = remote }
            callback.onDnsSdTxtRecordAvailable("KIGTTS-${id.take(10)}._kigtts-photo._tcp.local.", record, device)
            assertEquals(remote, found.single().p2p); assertEquals(id, found.single().id)
            assertEquals(key, found.single().token); assertEquals(54321, found.single().port)
            callback.onDnsSdTxtRecordAvailable("foreign._ipp._tcp.local.", record, device)
            assertEquals(1, found.size)
            wifi.stopDiscovery()
            assertEquals(1, radio.removedServices); assertEquals(1, radio.removedRequests)
            callback.onDnsSdTxtRecordAvailable("KIGTTS-${id.take(10)}._kigtts-photo._tcp.local.", record, device)
            assertEquals(1, found.size)
        } finally { wifi.stop() }
    }

    @Test fun aBusyServiceRequestRetriesAndDiscoveryDoesNotInterruptConnectionNegotiation() = runBlocking {
        radio.failedRequests = 1
        val wifi = FaceWifiDirect(context) {}
        wifi.start(FaceLanIdentity(faceToken(), "本人", 12345, faceToken()))
        try {
            assertEquals(0, radio.serviceDiscoveries)
            shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            assertEquals(2, radio.requestAttempts); assertEquals(1, radio.serviceDiscoveries)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            shadowOf(Looper.getMainLooper()).idleFor(15, TimeUnit.SECONDS)
            assertEquals(1, radio.serviceDiscoveries)
            pending.cancelAndJoin()
            shadowOf(Looper.getMainLooper()).idleFor(12, TimeUnit.SECONDS)
            assertEquals(2, radio.serviceDiscoveries)
        } finally { wifi.stop() }
    }

    @Test fun refusingLocalDeviceInformationDoesNotPreventServiceDiscovery() {
        radio.denyLocalAddress = true
        val wifi = FaceWifiDirect(context) {}
        try {
            wifi.start(FaceLanIdentity(faceToken(), "本人", 12345, faceToken()))
            assertNull(wifi.address); assertEquals(1, radio.serviceDiscoveries)
            announce(); assertEquals(1, radio.serviceDiscoveries)
        } finally { wifi.stop() }
    }

    @Implements(WifiP2pManager::class)
    class P2p {
        companion object { const val OWN = "22:33:44:55:66:77" }
        var group: WifiP2pGroup? = null
        var removed = 0; var cancelled = 0; var requested: String? = null; var failConnect = false
        var created = 0; var ownAddress = OWN; var removedServices = 0; var removedRequests = 0; var serviceDiscoveries = 0
        var failedRequests = 0; var requestAttempts = 0
        val requestQueries = mutableListOf<String>()
        var denyLocalAddress = false
        var txt: WifiP2pManager.DnsSdTxtRecordListener? = null
        @Implementation fun initialize(context: Context, looper: Looper, listener: WifiP2pManager.ChannelListener?) = Shadow.newInstanceOf(WifiP2pManager.Channel::class.java)
        @Implementation fun discoverPeers(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { listener?.onSuccess() }
        @Implementation fun stopPeerDiscovery(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { listener?.onSuccess() }
        @Implementation fun cancelConnect(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { cancelled++; listener?.onSuccess() }
        @Implementation fun requestDeviceInfo(channel: WifiP2pManager.Channel, listener: WifiP2pManager.DeviceInfoListener) {
            if (denyLocalAddress) throw SecurityException("Local address is unavailable")
            listener.onDeviceInfoAvailable(WifiP2pDevice().apply { deviceAddress = ownAddress })
        }
        @Implementation fun setDnsSdResponseListeners(channel: WifiP2pManager.Channel, services: WifiP2pManager.DnsSdServiceResponseListener?, listener: WifiP2pManager.DnsSdTxtRecordListener?) { txt = listener }
        @Implementation fun addLocalService(channel: WifiP2pManager.Channel, service: WifiP2pServiceInfo, listener: WifiP2pManager.ActionListener?) { listener?.onSuccess() }
        @Implementation fun removeLocalService(channel: WifiP2pManager.Channel, service: WifiP2pServiceInfo, listener: WifiP2pManager.ActionListener?) { removedServices++; listener?.onSuccess() }
        @Implementation fun addServiceRequest(channel: WifiP2pManager.Channel, request: WifiP2pServiceRequest, listener: WifiP2pManager.ActionListener?) {
            requestAttempts++
            requestQueries += ReflectionHelpers.callInstanceMethod<String>(request, "getSupplicantQuery")
            if (failedRequests > 0) { failedRequests--; listener?.onFailure(WifiP2pManager.BUSY) } else listener?.onSuccess()
        }
        @Implementation fun removeServiceRequest(channel: WifiP2pManager.Channel, request: WifiP2pServiceRequest, listener: WifiP2pManager.ActionListener?) { removedRequests++; listener?.onSuccess() }
        @Implementation fun discoverServices(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { serviceDiscoveries++; listener?.onSuccess() }
        @Implementation fun requestGroupInfo(channel: WifiP2pManager.Channel, listener: WifiP2pManager.GroupInfoListener) { listener.onGroupInfoAvailable(group) }
        @Implementation fun createGroup(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) {
            created++
            group = WifiP2pGroup().apply {
                ReflectionHelpers.setField(this, "mIsGroupOwner", true)
                ReflectionHelpers.setField(this, "mOwner", WifiP2pDevice().apply { deviceAddress = OWN })
            }; listener.onSuccess()
        }
        @Implementation fun removeGroup(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { removed++; group = null; listener?.onSuccess() }
        @Implementation fun connect(channel: WifiP2pManager.Channel, config: WifiP2pConfig, listener: WifiP2pManager.ActionListener) {
            requested = config.deviceAddress
            if (failConnect) listener.onFailure(WifiP2pManager.BUSY) else listener.onSuccess()
        }
        @Implementation fun requestConnectionInfo(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ConnectionInfoListener) {
            listener.onConnectionInfoAvailable(WifiP2pInfo().apply { groupFormed = true; isGroupOwner = false; groupOwnerAddress = InetAddress.getByName("192.168.49.1") })
        }
    }
}
