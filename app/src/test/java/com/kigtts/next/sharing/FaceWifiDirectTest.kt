package com.kigtts.next.sharing

import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.*
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

@RunWith(RobolectricTestRunner::class) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [29, 35], shadows = [FaceWifiDirectTest.P2p::class])
class FaceWifiDirectTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val radio = Shadow.extract<P2p>(context.getSystemService(WifiP2pManager::class.java))
    private val remote = "aa:bb:cc:dd:ee:ff"
    private fun connection(owner: String, ours: Boolean) = WifiP2pGroup().apply {
        ReflectionHelpers.setField(this, "mIsGroupOwner", ours)
        ReflectionHelpers.setField(this, "mOwner", WifiP2pDevice().apply { deviceAddress = owner })
    }
    private fun announce() { context.sendBroadcast(Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun twoReceivingPhonesLeaveOnlyTheirOwnGroupAndWaitForTheSelectedRecipient() = runBlocking {
        val wifi = FaceWifiDirect(context) {}
        wifi.start(true)
        try {
            assertTrue(radio.group!!.isGroupOwner)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            assertEquals(1, radio.removed); assertEquals(remote, radio.requested)
            radio.group = connection("11:22:33:44:55:66", false); announce()
            assertFalse(pending.isCompleted)
            radio.group = connection(remote, false); announce()
            assertEquals("192.168.49.1", withTimeout(3000) { pending.await() })
            assertEquals(P2p.OWN, wifi.address)
        } finally { wifi.stop() }
    }

    @Test fun aGroupCreatedElsewhereIsPreservedAndFailureUsesCurrentReceiveControls() = runBlocking {
        radio.group = connection(P2p.OWN, true); radio.failConnect = true
        val wifi = FaceWifiDirect(context) {}; wifi.start(true)
        try {
            val error = runCatching { wifi.connect(remote) }.exceptionOrNull()!!
            assertEquals(0, radio.removed)
            assertTrue(error.message!!.contains("开启面对面接收"))
            assertFalse(error.message!!.contains("面对面页面"))
        } finally { wifi.stop() }
    }

    @Test fun alreadyConnectedRecipientIsReusedAndCancellationStopsNegotiation() = runBlocking {
        val wifi = FaceWifiDirect(context) {}; wifi.start(false)
        try {
            radio.group = connection(remote, false)
            assertEquals("192.168.49.1", wifi.connect(remote)); assertNull(radio.requested)
            radio.group = null
            val pending = async(start = CoroutineStart.UNDISPATCHED) { wifi.connect(remote) }
            pending.cancelAndJoin(); assertEquals(1, radio.cancelled)
        } finally { wifi.stop() }
    }

    @Implements(WifiP2pManager::class)
    class P2p {
        companion object { const val OWN = "22:33:44:55:66:77" }
        var group: WifiP2pGroup? = null
        var removed = 0; var cancelled = 0; var requested: String? = null; var failConnect = false
        @Implementation fun initialize(context: Context, looper: Looper, listener: WifiP2pManager.ChannelListener?) = Shadow.newInstanceOf(WifiP2pManager.Channel::class.java)
        @Implementation fun discoverPeers(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { listener?.onSuccess() }
        @Implementation fun stopPeerDiscovery(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { listener?.onSuccess() }
        @Implementation fun cancelConnect(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) { cancelled++; listener?.onSuccess() }
        @Implementation fun requestDeviceInfo(channel: WifiP2pManager.Channel, listener: WifiP2pManager.DeviceInfoListener) { listener.onDeviceInfoAvailable(WifiP2pDevice().apply { deviceAddress = OWN }) }
        @Implementation fun requestGroupInfo(channel: WifiP2pManager.Channel, listener: WifiP2pManager.GroupInfoListener) { listener.onGroupInfoAvailable(group) }
        @Implementation fun createGroup(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) {
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
