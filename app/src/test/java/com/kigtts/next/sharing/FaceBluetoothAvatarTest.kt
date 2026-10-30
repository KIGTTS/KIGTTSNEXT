package com.kigtts.next.sharing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk = [29, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceBluetoothAvatarTest {
    @Test fun aDetailedPhotoFitsOneGattValueAndRemainsADecodableSquare() {
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(1234)
        val pixels = IntArray(96 * 96) { 0xff000000.toInt() or random.nextInt(0x1000000) }
        source.setPixels(pixels, 0, 96, 0, 0, 96, 96)
        val encoded = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.JPEG, 90, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
        source.recycle()
        val bytes = requireNotNull(faceBluetoothAvatar(encoded))
        assertTrue(bytes.size in 1..480)
        val thumbnail = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertEquals(thumbnail.width, thumbnail.height); assertTrue(thumbnail.width in 16..32)
        thumbnail.recycle()
        assertNull(faceBluetoothAvatar(null)); assertNull(faceBluetoothAvatar("invalid-image"))
    }

    @Test fun bluetoothPhotoSurvivesSparseDiscoveryButCannotReplaceTheResolvedFullAvatar() {
        val peer = FacePeer(faceToken(), "朋友", port = 12345, token = faceToken())
        val thumbnail = peer.copy(avatar = "small-photo", avatarFrame = "flower")
        val sparse = mergeFacePeer(thumbnail, peer.copy(p2p = "aa:bb:cc:dd:ee:ff"))
        assertEquals("small-photo", sparse.avatar); assertEquals("flower", sparse.avatarFrame)
        val full = facePeerProfile(peer, org.json.JSONObject().put("id", peer.id).put("protocol", 1)
            .put("avatar", "full-photo").put("avatarFrame", "flower"))
        assertEquals("full-photo", mergeFacePeer(full, thumbnail).avatar)
    }
}
