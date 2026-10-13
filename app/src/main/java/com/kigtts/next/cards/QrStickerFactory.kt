package com.kigtts.next.cards

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kigtts.plugin.api.StickerImage
import java.io.File
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

/** UTF-8 ECI and a four-module quiet zone preserve Unicode content across compliant readers. */
fun StickerRepository.createQrCode(text: String): StickerImage {
    stickerRequire(text.isNotEmpty(), message = "请填写二维码内容")
    val encoded = try { StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text)) }
        catch (_: Exception) { throw StickerFault(com.kigtts.plugin.api.StickerError.INVALID, "二维码内容包含无效字符") }
    stickerRequire(encoded.remaining() <= 2048, message = "二维码内容过长，请缩短后重试")
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1024, 1024, mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 4))
    val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE }
    val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    val file = File(context.cacheDir, "cards/qr/${UUID.randomUUID()}.png").apply { parentFile!!.mkdirs() }
    try {
        file.outputStream().use { stickerRequire(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it), message = "二维码生成失败") }
        return importResource(file)
    } finally { bitmap.recycle(); file.delete() }
}
