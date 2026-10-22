package com.kigtts.next.io

import java.io.InputStream

/** API 26-compatible bounded read; one extra byte lets callers detect oversized documents. */
internal fun InputStream.readUpTo(limit: Int): ByteArray {
    require(limit >= 0)
    val bytes = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val count = read(bytes, total, limit - total)
        if (count < 0) break
        if (count == 0) {
            val next = read()
            if (next < 0) break
            bytes[total++] = next.toByte()
        } else total += count
    }
    return bytes.copyOf(total)
}
