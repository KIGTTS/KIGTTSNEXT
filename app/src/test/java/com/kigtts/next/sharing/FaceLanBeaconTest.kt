package com.kigtts.next.sharing

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.*
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FaceLanBeaconTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private fun identity(port: Int = 12345) = FaceLanIdentity(faceToken(), "测试手机", port, faceToken())

    @Test fun twoSendingPeersDiscoverEachOtherWithoutEitherSwitchingToReceive() {
        val a = identity(); val b = identity()
        val socketA = DatagramSocket(0, loopback); val socketB = DatagramSocket(0, loopback)
        val foundA = LinkedBlockingQueue<FacePeer>(); val foundB = LinkedBlockingQueue<FacePeer>()
        val beaconA = FaceLanBeacon(a, { listOf(InetSocketAddress(loopback, socketB.localPort)) }, foundA::offer, {}, { socketA })
        val beaconB = FaceLanBeacon(b, { listOf(InetSocketAddress(loopback, socketA.localPort)) }, foundB::offer, {}, { socketB })
        try {
            beaconA.start(); beaconB.start()
            val peerB = requireNotNull(foundA.poll(6, TimeUnit.SECONDS)); val peerA = requireNotNull(foundB.poll(6, TimeUnit.SECONDS))
            assertEquals(b.id, peerB.id); assertEquals(b.token, peerB.token); assertEquals(b.port, peerB.port)
            assertEquals(a.id, peerA.id); assertEquals(a.token, peerA.token); assertEquals("127.0.0.1", peerA.host)
        } finally { beaconA.close(); beaconB.close() }
        assertTrue(socketA.isClosed); assertTrue(socketB.isClosed)
    }

    @Test fun discoveredEndpointTransfersBytesOnlyAfterExplicitApproval() = runBlocking {
        val root = Files.createTempDirectory("face-lan-transfer").toFile()
        var received = 0
        val receiver = FacePhotoSession(root.resolve("session"), { JSONObject().put("nickname", "接收者") }) { _, _ -> received++ }
        val a = identity(); val b = FaceLanIdentity(faceToken(), "接收者", receiver.server.port, receiver.peerToken)
        val socketA = DatagramSocket(0, loopback); val socketB = DatagramSocket(0, loopback)
        val found = LinkedBlockingQueue<FacePeer>()
        val beaconA = FaceLanBeacon(a, { listOf(InetSocketAddress(loopback, socketB.localPort)) }, found::offer, {}, { socketA })
        val beaconB = FaceLanBeacon(b, { listOf(InetSocketAddress(loopback, socketA.localPort)) }, {}, {}, { socketB })
        try {
            beaconA.start(); beaconB.start()
            val peer = requireNotNull(found.poll(6, TimeUnit.SECONDS))
            val file = root.resolve("photo.jpg").apply { writeText("real transferred photo bytes") }
            val asset = FaceAsset(faceToken(), faceToken(), "original", file.name, "image/jpeg", file.length(), sha256(file), "SQUARE", file)
            val transfer = async(Dispatchers.IO) { FaceTransferClient.send(peer, faceToken(), JSONObject(), listOf(asset)) {} }
            withTimeout(5_000) { while (receiver.requests.value.isEmpty()) delay(10) }
            assertEquals(0, received); assertFalse(transfer.isCompleted)
            receiver.approve(receiver.requests.value.single().id, false)
            withTimeout(5_000) { transfer.await() }; assertEquals(1, received)
        } finally { beaconA.close(); beaconB.close(); receiver.close(); root.deleteRecursively() }
    }

    @Test fun malformedForeignOversizedAndPublicPacketsAreIgnored() {
        val self = identity(); val other = identity()
        fun parse(bytes: ByteArray, host: String = "192.168.1.2") = FaceLanProtocol.parse(bytes, bytes.size, InetAddress.getByName(host))
        assertNull(parse("garbage".toByteArray()))
        assertNull(parse(ByteArray(FACE_LAN_PACKET_LIMIT + 1) { 32 }))
        assertNull(parse(FaceLanProtocol.query(other.id), "8.8.8.8"))
        assertNull(parse("{\"protocol\":\"another-app\",\"id\":\"${other.id}\"}".toByteArray()))
        val response = requireNotNull(parse(FaceLanProtocol.response(other, self.id)))
        assertNotNull(FaceLanProtocol.peer(response, loopback, self.id))
        assertNull(FaceLanProtocol.peer(response, loopback, faceToken()))
        assertNull(FaceLanProtocol.peer(response.put("key", "bad"), loopback, self.id))
        response.put("key", other.token).put("port", 65536)
        assertNull(FaceLanProtocol.peer(response, loopback, self.id))
    }

    @Test fun closingPageStopsReplyingAndAnOldPageCannotRestartItself() {
        val self = identity(); val requester = identity()
        val server = DatagramSocket(0, loopback)
        val beacon = FaceLanBeacon(self, { emptyList() }, {}, {}, { server })
        DatagramSocket(0, loopback).use { client ->
            client.soTimeout = 2_000
            val query = FaceLanProtocol.query(requester.id)
            val target = InetSocketAddress(loopback, server.localPort)
            try {
                beacon.start(); client.send(DatagramPacket(query, query.size, target))
                val reply = DatagramPacket(ByteArray(1024), 1024); client.receive(reply)
                assertNotNull(FaceLanProtocol.parse(reply.data, reply.length, reply.address))
                beacon.close(); beacon.start(); assertTrue(server.isClosed)
                client.soTimeout = 300
                client.send(DatagramPacket(query, query.size, target))
                assertTrue(runCatching { client.receive(reply) }.exceptionOrNull() is SocketTimeoutException)
            } finally { beacon.close() }
        }
    }

    @Test fun subnetBroadcastsUseTheRealPrefixAndSkipPublicVpnAndPointToPointAddresses() {
        fun broadcast(ip: String, prefix: Int) = faceLanBroadcast(InetAddress.getByName(ip), prefix)?.hostAddress
        assertEquals("192.168.1.255", broadcast("192.168.1.3", 24))
        assertEquals("172.16.255.255", broadcast("172.16.4.3", 16))
        assertEquals("10.3.5.255", broadcast("10.3.4.7", 23))
        assertNull(broadcast("8.8.8.8", 24)); assertNull(broadcast("192.168.1.3", 31))
        assertNull(broadcast("fe80::1234", 64))
    }
}
