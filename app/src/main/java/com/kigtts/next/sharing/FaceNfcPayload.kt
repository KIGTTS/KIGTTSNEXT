package com.kigtts.next.sharing

import android.nfc.NdefMessage
import android.nfc.NdefRecord

/** Standard WSC credential record; let the receiving system confirm joining the temporary Wi-Fi. */
internal fun faceHotspotNdef(details: HotspotDetails): ByteArray {
    fun attribute(id: Int, value: ByteArray) = byteArrayOf((id shr 8).toByte(), id.toByte(), (value.size shr 8).toByte(), value.size.toByte()) + value
    val ssid = details.ssid.toByteArray(Charsets.UTF_8)
    val password = details.password.toByteArray(Charsets.UTF_8)
    require(ssid.size in 1..32 && password.size in 8..64)
    val credential = attribute(0x1026, byteArrayOf(1)) + attribute(0x1045, ssid) +
        attribute(0x1003, byteArrayOf(0, 0x20)) + attribute(0x100F, byteArrayOf(0, 8)) +
        attribute(0x1027, password) + attribute(0x1020, ByteArray(6))
    val data = attribute(0x104A, byteArrayOf(0x10)) + attribute(0x100E, credential)
    return NdefMessage(arrayOf(NdefRecord.createMime("application/vnd.wfa.wsc", data))).toByteArray()
}

internal fun faceCollectionNdef(url: String): ByteArray = NdefMessage(arrayOf(NdefRecord.createUri(url))).toByteArray()
