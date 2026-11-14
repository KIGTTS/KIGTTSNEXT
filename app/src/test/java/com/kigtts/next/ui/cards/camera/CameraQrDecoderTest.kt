package com.kigtts.next.ui.cards.camera

import com.google.zxing.RGBLuminanceSource
import com.kigtts.next.identity.profileQrMatrix
import org.junit.Assert.*
import org.junit.Test

class CameraQrDecoderTest {
    @Test fun unicodePayloadAndInvertedModulesDecodeWithoutLosingText() {
        val code = "你好，KIGTTS！👋 日本語"
        val pixels = IntArray(240 * 240) { 0xff101010.toInt() }
        paint(pixels, 240, code, 16, 16, inverted = true)
        assertEquals(code, CameraCodeDecoder().decode(RGBLuminanceSource(240, 240, pixels)).single().text)
    }

    @Test fun adjacentCodesRetainSeparateCentersInSameFrame() {
        val pixels = IntArray(420 * 240) { -1 }
        paint(pixels, 420, "左侧", 12, 30)
        paint(pixels, 420, "右侧", 224, 30)
        val codes = CameraCodeDecoder().decode(RGBLuminanceSource(420, 240, pixels)).associateBy { it.text }
        assertEquals(setOf("左侧", "右侧"), codes.keys)
        assertTrue(requireNotNull(codes.getValue("左侧").center).x < 200f)
        assertTrue(requireNotNull(codes.getValue("右侧").center).x > 224f)
    }

    @Test fun qqRoutingAcceptsOfficialShortLinksButRejectsLookalikeDomainsAndSchemes() {
        assertTrue(isQqScannerCode("https://qm.qq.com/q/test"))
        assertTrue(isQqScannerCode("mqqapi://card/show_pslcard?uin=1234"))
        assertFalse(isQqScannerCode("https://qm.qq.com.example.org/q/test"))
        assertFalse(isQqScannerCode("https://example.org@qm.qq.com/q/test"))
        assertFalse(isQqScannerCode("intent://qm.qq.com/q/test"))
    }

    private fun paint(pixels: IntArray, width: Int, text: String, left: Int, top: Int, inverted: Boolean = false) {
        val matrix = requireNotNull(profileQrMatrix(text))
        val scale = 4
        repeat(matrix.height * scale) { y -> repeat(matrix.width * scale) { x ->
            pixels[(y + top) * width + x + left] = if (matrix[x / scale, y / scale] != inverted) 0xff101010.toInt() else -1
        } }
    }
}
