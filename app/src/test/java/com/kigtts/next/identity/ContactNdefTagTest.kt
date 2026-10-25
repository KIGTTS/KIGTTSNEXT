package com.kigtts.next.identity

import org.junit.Assert.*
import org.junit.Test

class ContactNdefTagTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun tagIsReadableInChunksReadOnlyAndExpiresWithTheOpenContactPage() {
        var payload: ByteArray? = ByteArray(400) { it.toByte() }
        val tag = ContactNdefTag { payload }
        assertArrayEquals(bytes("6985"), tag.respond(bytes("00B0000002")))
        assertArrayEquals(bytes("9000"), tag.respond(bytes("00A4040007D276000085010100")))
        assertArrayEquals(bytes("9000"), tag.respond(bytes("00A4000C02E103")))
        val cc = tag.respond(bytes("00B000000F")); assertEquals(17, cc.size); assertArrayEquals(bytes("9000"), cc.takeLast(2).toByteArray())
        assertArrayEquals(bytes("9000"), tag.respond(bytes("00A4000C02E104")))
        val header = tag.respond(bytes("00B0000002")); assertArrayEquals(bytes("01909000"), header)
        val first = tag.respond(bytes("00B0000200")); assertEquals(258, first.size); assertArrayEquals(payload!!.copyOfRange(0, 256), first.take(256).toByteArray())
        val tail = tag.respond(bytes("00B0010200")); assertArrayEquals(payload!!.copyOfRange(256, 400), tail.dropLast(2).toByteArray())
        assertArrayEquals(bytes("6D00"), tag.respond(bytes("00D600000100")))
        payload = null; assertArrayEquals(bytes("6985"), tag.respond(bytes("00B0000002")))
        payload = byteArrayOf(1); assertArrayEquals(bytes("6985"), tag.respond(bytes("00B0000002")))
    }
}
