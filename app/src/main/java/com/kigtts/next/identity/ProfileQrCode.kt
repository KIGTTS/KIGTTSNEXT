package com.kigtts.next.identity

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

internal const val PROFILE_QR_MAX_BYTES = 1000

internal fun profileQrMatrix(value: String): BitMatrix? {
    if (value.isBlank() || value.toByteArray(Charsets.UTF_8).size > PROFILE_QR_MAX_BYTES) return null
    return try {
        QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 1, 1,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
    } catch (_: WriterException) { null }
}

internal fun BitMatrix.qrDotAllowed(x: Int, y: Int): Boolean {
    val start = 4; val size = 7; val end = width - start
    return !(x < start + size && y < start + size ||
        x >= end - size && y < start + size || x < start + size && y >= end - size)
}
