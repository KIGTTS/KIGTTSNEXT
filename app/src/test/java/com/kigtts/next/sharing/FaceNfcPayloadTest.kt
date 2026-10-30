package com.kigtts.next.sharing

import android.nfc.NdefMessage
import com.kigtts.next.identity.ContactNfcSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class FaceNfcPayloadTest {
    private fun attributes(bytes: ByteArray): Map<Int, ByteArray> {
        val result = linkedMapOf<Int, ByteArray>(); var offset = 0
        fun word(i: Int) = ((bytes[i].toInt() and 255) shl 8) or (bytes[i + 1].toInt() and 255)
        while (offset < bytes.size) {
            val id = word(offset); val count = word(offset + 2); offset += 4
            result[id] = bytes.copyOfRange(offset, offset + count); offset += count
        }
        assertEquals(bytes.size, offset)
        return result
    }
    @Test fun hotspotHasStandardWscCredentialsAndUtf8Ssid() {
        val details = HotspotDetails("KIGTTS 临时网络", "test:password")
        val record = NdefMessage(faceHotspotNdef(details)).records.single()
        assertEquals("application/vnd.wfa.wsc", record.toMimeType())
        val credential = attributes(attributes(record.payload).getValue(0x100E))
        assertEquals(details.ssid, credential.getValue(0x1045).toString(Charsets.UTF_8))
        assertEquals(details.password, credential.getValue(0x1027).toString(Charsets.UTF_8))
        assertArrayEquals(byteArrayOf(0, 0x20), credential.getValue(0x1003))
        assertArrayEquals(byteArrayOf(0, 8), credential.getValue(0x100F))
    }
    @Test fun collectionUriReplacesWifiPayloadAndEndingOldSessionCannotEraseNewOne() {
        val wifi = ContactNfcSession.begin(faceHotspotNdef(HotspotDetails("KIGTTS", "password")))
        val uri = "http://192.168.49.1:40001/s/${faceToken()}/"
        val payload = faceCollectionNdef(uri)
        val collection = ContactNfcSession.begin(payload)
        ContactNfcSession.end(wifi)
        assertEquals(uri, NdefMessage(requireNotNull(ContactNfcSession.payload())).records.single().toUri().toString())
        ContactNfcSession.end(collection)
        assertNull(ContactNfcSession.payload())
    }
}
