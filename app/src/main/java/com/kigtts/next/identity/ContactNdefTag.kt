package com.kigtts.next.identity

/** Read-only NFC Forum Type 4 NDEF application. No command can alter a contact. */
internal class ContactNdefTag(private val payload: () -> ByteArray?) {
    private var selectedApp = false
    private var file = 0
    fun reset() { selectedApp = false; file = 0 }
    fun respond(command: ByteArray): ByteArray {
        val content = payload() ?: return status(0x6985).also { reset() }
        if (content.size > 8190 || command.size < 4) return status(0x6700)
        fun u(i: Int) = command[i].toInt() and 255
        if (u(0) != 0) return status(0x6E00)
        return when (u(1)) {
            0xA4 -> {
                if (command.size < 5 || command.size < 5 + u(4)) return status(0x6700)
                val data = command.copyOfRange(5, 5 + u(4))
                when {
                    u(2) == 4 -> {
                        selectedApp = data.contentEquals(byteArrayOf(0xD2.toByte(), 0x76, 0, 0, 0x85.toByte(), 1, 1)); file = 0
                        status(if (selectedApp) 0x9000 else 0x6A82)
                    }
                    selectedApp && u(2) == 0 && data.size == 2 -> {
                        file = ((data[0].toInt() and 255) shl 8) or (data[1].toInt() and 255)
                        status(if (file in setOf(0xE103, 0xE104)) 0x9000 else 0x6A82)
                    }
                    else -> status(0x6A82)
                }
            }
            0xB0 -> {
                if (!selectedApp || file !in setOf(0xE103, 0xE104)) return status(0x6985)
                if (command.size != 5) return status(0x6700)
                val bytes = if (file == 0xE103) byteArrayOf(0, 15, 0x20, 1, 0, 1, 0, 4, 6, 0xE1.toByte(), 4, 0x20, 0, 0, 0xFF.toByte())
                    else byteArrayOf((content.size shr 8).toByte(), content.size.toByte()) + content
                val offset = (u(2) shl 8) or u(3)
                if (offset >= bytes.size) return status(0x6B00)
                bytes.copyOfRange(offset, minOf(bytes.size, offset + (u(4).takeIf { it > 0 } ?: 256))) + status(0x9000)
            }
            else -> status(0x6D00)
        }
    }
    private fun status(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
}
