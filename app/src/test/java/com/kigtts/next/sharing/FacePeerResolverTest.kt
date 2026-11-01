package com.kigtts.next.sharing

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk = [29, 35])
class FacePeerResolverTest {
    @Test fun reachableAddressIsProbedWithoutWaitingForTheFirstStaleAddress() = runBlocking {
        val source = peer().copy(hosts = listOf("192.168.1.2", "192.168.49.2"))
        val slowEntered = CountDownLatch(1); val fastEntered = CountDownLatch(1)
        val resolver = FacePeerResolver(this, faceToken(), {}) { _, host ->
            if (host == "192.168.1.2") {
                slowEntered.countDown()
                assertTrue("可用地址不应被前一个地址阻塞", fastEntered.await(2, TimeUnit.SECONDS))
                throw IOException("stale")
            }
            assertTrue(slowEntered.await(2, TimeUnit.SECONDS)); fastEntered.countDown(); profile(source)
        }
        try { assertEquals("192.168.49.2", resolver.resolve(source)?.host) } finally { resolver.clear() }
    }
    private fun peer() = FacePeer(faceToken(), "KIGTTS 用户", port = 12345, token = faceToken())
    private fun profile(peer: FacePeer) = JSONObject().put("id", peer.id).put("protocol", 1)
        .put("nickname", "真正的昵称").put("avatar", "avatar-data").put("avatarFrame", "flower")

    @Test fun sparseDiscoveryKeepsTheResolvedIdentityButNewSessionDropsOldEndpoints() {
        val initial = peer().copy(host = "192.168.1.2", p2p = "aa:bb:cc:dd:ee:ff")
        val resolved = facePeerProfile(initial, profile(initial))
        val merged = mergeFacePeer(resolved, initial.copy(host = null, p2p = null, signal = -30))
        assertEquals("真正的昵称", merged.name); assertEquals("avatar-data", merged.avatar)
        assertEquals("flower", merged.avatarFrame); assertEquals(initial.host, merged.host)
        assertEquals(initial.p2p, merged.p2p)
        val clearedAvatar = mergeFacePeer(resolved, facePeerProfile(initial, profile(initial).put("avatar", "")))
        assertNull(clearedAvatar.avatar)
        val next = mergeFacePeer(resolved, initial.copy(token = faceToken(), host = null))
        assertNull(next.profile); assertNull(next.avatar); assertNull(next.host)
    }

    @Test fun bluetoothCarriesPrivateWifiCandidatesAndFrameAndRejectsInvalidSessions() {
        val source = peer()
        val json = faceBluetoothDescriptor(source.id, profile(source), source.port, source.token, "aa:bb:cc:dd:ee:ff",
            listOf("192.168.1.2", "8.8.8.8", "example.com", "192.168.1.2", "10.1.2.3"))
        val found = faceBluetoothPeer(json, "bluetooth", -40)
        assertEquals(listOf("192.168.1.2", "10.1.2.3"), found.hosts)
        assertEquals("真正的昵称", found.name); assertEquals("flower", found.avatarFrame)
        assertTrue(json.toString().toByteArray().size < 512)
        assertTrue(runCatching { faceBluetoothPeer(JSONObject(json.toString()).put("token", "bad"), "bluetooth", -40) }.isFailure)
        assertTrue(runCatching { faceBluetoothPeer(JSONObject(json.toString()).put("port", 0), "bluetooth", -40) }.isFailure)
        val legacy = faceBluetoothPeer(JSONObject(json.toString()).removeHosts(), "bluetooth", -40)
        assertTrue(legacy.hosts.isEmpty()); assertEquals(found.p2p, legacy.p2p)
    }
    private fun JSONObject.removeHosts() = apply { remove("hosts") }

    @Test fun failedProfileLookupCanRetryAndWrongIdentityIsNeverPublished() = runBlocking {
        val source = peer().copy(hosts = listOf("192.168.1.2"))
        var now = 0L; var calls = 0; val found = mutableListOf<FacePeer>()
        val resolver = FacePeerResolver(this, faceToken(), found::add, { now }) { _, _ ->
            calls++; when (calls) { 1 -> throw IOException("temporary"); 2 -> profile(source).put("id", faceToken()); else -> profile(source) }
        }
        resolver.refresh(source)?.join(); assertEquals(1, calls); assertTrue(found.isEmpty())
        now = 1000; assertNull(resolver.refresh(source)); assertEquals(1, calls)
        now = 3001; resolver.refresh(source)?.join(); assertEquals(2, calls); assertTrue(found.isEmpty())
        now = 6002; resolver.refresh(source)?.join(); assertEquals("真正的昵称", found.single().name)
        resolver.clear()
    }

    @Test fun closingDiscoveryDuringAnInFlightLookupDoesNotResurrectThePeer() = runBlocking {
        val source = peer().copy(hosts = listOf("192.168.1.2"))
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val found = mutableListOf<FacePeer>()
        val resolver = FacePeerResolver(this, faceToken(), found::add) { _, _ ->
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); profile(source)
        }
        val job = resolver.refresh(source)!!
        yield()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); resolver.clear() }
        finally { release.countDown() }
        job.join(); assertTrue(found.isEmpty())
    }

    @Test fun bluetoothOnlyDiscoveryResolvesTheRealLocalServerAndTransfersAfterApproval() = runBlocking {
        val root = Files.createTempDirectory("face-resolve-transfer").toFile()
        val receiver = peer(); var deliveries = 0
        val server = FacePhotoSession(File(root, "received"), { profile(receiver) }) { _, assets ->
            assertEquals("actual-photo-bytes", assets.single().file!!.readText()); deliveries++
        }
        val source = receiver.copy(port = server.server.port, token = server.peerToken, hosts = listOf("127.0.0.1"))
        val resolver = FacePeerResolver(this, faceToken(), {})
        try {
            val resolved = requireNotNull(resolver.resolve(source))
            assertEquals("127.0.0.1", resolved.host); assertEquals("真正的昵称", resolved.name)
            assertEquals("avatar-data", resolved.avatar); assertEquals("flower", resolved.avatarFrame)
            val photo = File(root, "photo.jpg").apply { writeText("actual-photo-bytes") }
            val asset = FaceAsset(faceToken(), faceToken(), "polaroid", "photo.jpg", "image/jpeg", photo.length(), sha256(photo), "SQUARE", photo)
            val transfer = async { FaceTransferClient.send(resolved, faceToken(), JSONObject().put("nickname", "发送方"), listOf(asset)) {} }
            withTimeout(5000) { while (server.requests.value.isEmpty()) delay(10) }
            assertEquals(0, deliveries); server.approve(server.requests.value.single().id, false)
            withTimeout(5000) { transfer.await() }; assertEquals(1, deliveries)
        } finally { resolver.clear(); server.close(); root.deleteRecursively() }
    }
}
