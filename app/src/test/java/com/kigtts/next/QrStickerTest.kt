package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QrStickerTest {
    private fun repository(): StickerRepository {
        val app = ApplicationProvider.getApplicationContext<Application>(); val paths = PluginPaths(app)
        return StickerRepository(app, paths, MediaRepository(app, paths))
    }
    @Test fun actualGeneratedPngDecodesChineseJapaneseEmojiAndCombiningCharactersExactly() {
        val repo = repository()
        val content = "中文 日本語 🙂🦊\nالعربية\ne\u0301 与 é\nhttps://例子.测试/贴纸?q=你好"
        val image = repo.createQrCode(content)
        val bitmap = requireNotNull(repo.imageFile(image.id)).inputStream().use { BitmapFactory.decodeStream(it) }
        try {
            val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
            assertEquals(content, decoded.text); assertEquals("image/png", image.mime)
        } finally { bitmap.recycle() }
    }
    @Test fun invalidUnicodeAndOverlongUtf8AreRejectedBeforeTheGalleryChanges() {
        val repo = repository(); val before = repo.snapshot.value
        for (text in listOf("\uD800", "🙂".repeat(513))) {
            try { repo.createQrCode(text); fail("Invalid QR payload should fail") } catch (_: IllegalArgumentException) {}
            assertEquals(before, repo.snapshot.value)
        }
    }
}
