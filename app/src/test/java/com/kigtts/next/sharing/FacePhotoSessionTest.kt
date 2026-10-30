package com.kigtts.next.sharing

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FacePhotoSessionTest {
    @Test fun endingBrowserShareRevokesItsUrlWithoutLosingPendingReceiveRequests() {
        val root = Files.createTempDirectory("face-end-browser").toFile()
        val server = FacePhotoSession(root.resolve("session"), { JSONObject().put("nickname", "receiver") }) { _, _ -> }
        try {
            val file = asset(root); server.assets[file.id] = file
            val oldPath = server.path; val peerPath = "/s/${server.peerToken}/"
            val offer = JSONObject().put("id", faceToken()).put("session", faceToken()).put("profile", JSONObject().put("nickname", "朋友"))
                .put("assets", JSONArray().put(file.json()))
            val reply = FaceTransferClient.request("127.0.0.1", server.server.port, "POST", peerPath + "offer", offer.toString().toByteArray())
            assertEquals("waiting", reply.getString("status"))
            server.endBrowserShare()
            assertEquals(404, get(server.server.port, oldPath).code)
            assertEquals(404, get(server.server.port, oldPath + "file/${file.id}").code)
            assertTrue(server.assets.isEmpty())
            assertEquals(1, server.requests.value.size)
            assertEquals(200, get(server.server.port, peerPath + "hello").code)
            server.approve(reply.getString("id"), false)
            assertTrue(server.requests.value.isEmpty())
            val status = FaceTransferClient.request("127.0.0.1", server.server.port, "GET", peerPath + "offer/${reply.getString("id")}")
            assertEquals("accepted", status.getString("status"))
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun discoverablePeerTokenCannotOpenTheBrowserOrDownloadPreparedPhotos() {
        val root = Files.createTempDirectory("face-peer-isolation").toFile()
        val server = FacePhotoSession(root.resolve("session"), { JSONObject().put("nickname", "sender") }) { _, _ -> }
        try {
            val file = asset(root); server.assets[file.id] = file
            val peerPath = "/s/${server.peerToken}/"
            assertNotEquals(server.token, server.peerToken)
            assertEquals(200, get(server.server.port, peerPath + "hello").code)
            assertEquals(404, get(server.server.port, peerPath).code)
            assertEquals(404, get(server.server.port, peerPath + "file/${file.id}").code)
            assertEquals(404, get(server.server.port, peerPath + "download?ids=${file.id}").code)
            assertArrayEquals(file.file!!.readBytes(), get(server.server.port, server.path + "file/${file.id}").body)
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun profileWithAvatarAndTilesCanCrossTheFormer64KiBTransportLimit() {
        val root = Files.createTempDirectory("face-profile-large").toFile()
        val profile = JSONObject().put("nickname", "资料").put("avatar", "A".repeat(60000))
            .put("tiles", JSONArray((1..20).map { JSONObject().put("id", "tile$it").put("text", "中文".repeat(150)) }))
        val server = FacePhotoSession(File(root, "session"), { profile }) { _, _ -> }
        try {
            val hello = FaceTransferClient.request("127.0.0.1", server.server.port, "GET", server.path + "hello")
            assertEquals(60000, hello.getString("avatar").length)
            val file = asset(root)
            val offer = JSONObject().put("profile", profile).put("session", faceToken()).put("id", faceToken()).put("assets", JSONArray().put(file.json()))
            val reply = FaceTransferClient.request("127.0.0.1", server.server.port, "POST", server.path + "offer", offer.toString().toByteArray(Charsets.UTF_8))
            assertEquals("waiting", reply.getString("status")); assertEquals(1, server.requests.value.size)
        } finally { server.close(); root.deleteRecursively() }
    }
    private fun asset(root: File, body: String = "photo bytes") = File(root, "source.jpg").apply { writeText(body) }.let {
        FaceAsset(faceToken(), faceToken(), "original", "照片.jpg", "image/jpeg", it.length(), sha256(it), "SQUARE", it)
    }
    private data class Reply(val code: Int, val body: ByteArray)
    private fun get(port: Int, path: String): Reply = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 3000
        socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
        val bytes = socket.getInputStream().readBytes()
        val split = bytes.indices.first { index -> index + 3 < bytes.size && bytes[index] == 13.toByte() && bytes[index + 1] == 10.toByte() && bytes[index + 2] == 13.toByte() && bytes[index + 3] == 10.toByte() }
        Reply(String(bytes.copyOfRange(0, split)).substringAfter(' ').substringBefore(' ').toInt(), bytes.copyOfRange(split + 4, bytes.size))
    }
    @Test fun browserOffersOnlyRegisteredFilesAndSelectedZipAndClosingRevokesTheUrl() {
        val root = Files.createTempDirectory("face-http").toFile()
        val profile = JSONObject().put("nickname", "<script>alert(1)</script>").put("bio", "hello & goodbye")
        val server = FacePhotoSession(File(root, "session"), { profile }) { _, _ -> fail("Browser downloads cannot import files") }
        val first = asset(root); val secondFile = File(root, "two.png").apply { writeText("second image") }
        val second = first.copy(id = faceToken(), file = secondFile, bytes = secondFile.length(), sha = sha256(secondFile), variant = "polaroid")
        val port = server.server.port
        try {
            server.assets[first.id] = first; server.assets[second.id] = second
            assertEquals(404, get(port, "/").code)
            assertEquals(404, get(port, "/s/${faceToken()}/hello").code)
            val page = String(get(port, server.path).body)
            assertTrue(page.contains("&lt;script&gt;")); assertFalse(page.contains("<script>alert(1)"))
            assertArrayEquals(first.file!!.readBytes(), get(port, server.path + "file/${first.id}").body)
            assertEquals(404, get(port, server.path + "file/not-registered").code)
            val zip = get(port, server.path + "download?ids=${second.id}")
            assertEquals(200, zip.code)
            ZipInputStream(ByteArrayInputStream(zip.body)).use { stream ->
                assertNotNull(stream.nextEntry); assertArrayEquals(secondFile.readBytes(), stream.readBytes()); assertNull(stream.nextEntry)
            }
        } finally { server.close(); root.deleteRecursively() }
        assertTrue(runCatching { get(port, "/") }.isFailure)
    }
    @Test fun nativeUploadsRequireConsentValidateHashAndAutoConsentStaysWithinOneSession() {
        val root = Files.createTempDirectory("face-native").toFile(); var deliveries = 0
        val server = FacePhotoSession(File(root, "session"), { JSONObject().put("nickname", "receiver") }) { _, files ->
            assertArrayEquals("photo bytes".toByteArray(), files.single().file!!.readBytes()); deliveries++
        }
        val file = asset(root); val client = faceToken(); val id = faceToken()
        val port = server.server.port; val prefix = server.path
        fun offer(request: String, session: String): JSONObject = FaceTransferClient.request("127.0.0.1", port, "POST", prefix + "offer",
            JSONObject().put("id", request).put("session", session).put("profile", JSONObject().put("nickname", "sender"))
                .put("assets", JSONArray(listOf(file.json()))).toString().toByteArray())
        fun upload(request: String, source: File = file.file!!) = FaceTransferClient.request("127.0.0.1", port, "PUT", prefix + "receive/$request/${file.id}",
            file = source, headers = mapOf("X-KIGTTS-Session" to client))
        try {
            assertEquals("waiting", offer(id, client).getString("status")); assertEquals(1, server.requests.value.size)
            assertTrue(runCatching { upload(id) }.isFailure); assertEquals(0, deliveries)
            server.approve(id, true); assertTrue(server.requests.value.isEmpty())
            val corrupt = File(root, "corrupt.jpg").apply { writeText("wrong bytes") }
            assertTrue(runCatching { upload(id, corrupt) }.isFailure); assertEquals(0, deliveries)
            upload(id); assertEquals(1, deliveries)
            upload(id); assertEquals(1, deliveries)
            assertEquals("complete", offer(id, client).getString("status"))
            assertEquals("accepted", offer(faceToken(), client).getString("status"))
            val stranger = faceToken(); assertEquals("waiting", offer(stranger, faceToken()).getString("status"))
            server.reject(stranger); assertTrue(server.requests.value.isEmpty())
            server.stopAutoReceive(); assertEquals("waiting", offer(faceToken(), client).getString("status"))
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun nativeClientWaitsForApprovalAndTransfersRealBytesBeforeReportingCompletion() = runBlocking {
        val root = Files.createTempDirectory("face-client").toFile(); var deliveries = 0
        val server = FacePhotoSession(File(root, "session"), { JSONObject() }) { _, _ -> deliveries++ }
        val asset = asset(root)
        try {
            val transfer = async(Dispatchers.IO) { FaceTransferClient.send(FacePeer("receiver", "receiver", "127.0.0.1", server.server.port, server.token),
                faceToken(), JSONObject().put("nickname", "sender"), listOf(asset)) {} }
            withTimeout(5000) { while (server.requests.value.isEmpty()) delay(10) }
            assertEquals(0, deliveries); assertFalse(transfer.isCompleted)
            server.approve(server.requests.value.single().id, false)
            withTimeout(5000) { transfer.await() }; assertEquals(1, deliveries)
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun malformedManifestsAndWifiQrSeparatorsAreHandledBeforePublishing() {
        val connection = HotspotDetails("a;b\\c", "p:q;z")
        assertEquals("WIFI:T:WPA;S:a\\;b\\\\c;P:p\\:q\\;z;;", connection.qr())
        val root = Files.createTempDirectory("face-schema").toFile()
        try {
            val asset = asset(root)
            assertTrue(runCatching { FaceAsset.parse(asset.json().put("bytes", -1)) }.isFailure)
            assertTrue(runCatching { FaceAsset.parse(asset.json().put("id", "../../private")) }.isFailure)
            assertTrue(runCatching { FaceAsset.parse(asset.json().put("mime", "text/html")) }.isFailure)
            assertTrue(runCatching { assetsFrom(JSONObject().put("assets", JSONArray(listOf(asset.json(), asset.json())))) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
