package com.kigtts.next.identity

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.util.UUID

/** Nothing is shared unless a foreground QR dialog owns a live session. */
internal object ContactNfcSession {
    @Volatile private var current: Pair<String, ByteArray>? = null
    fun begin(payload: ByteArray): String = UUID.randomUUID().toString().also { current = it to payload.copyOf() }
    fun end(id: String) { synchronized(this) { if (current?.first == id) current = null } }
    fun payload(): ByteArray? = current?.second
}

class ContactNfcService : HostApduService() {
    private val tag = ContactNdefTag(ContactNfcSession::payload)
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray = tag.respond(commandApdu)
    override fun onDeactivated(reason: Int) = tag.reset()
}
