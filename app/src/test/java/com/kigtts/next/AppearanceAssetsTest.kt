package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class AppearanceAssetsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Test fun imagesAreDurableBoundedAndDoNotOverwriteOriginal() {
        val input = File(app.cacheDir, "appearance-original.png")
        Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888).also { bitmap ->
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val original = AppearanceAssets.digest(input.readBytes())
        val assets = AppearanceAssets(app); val key = assets.importBackground(Uri.fromFile(input))
        assertEquals(original, AppearanceAssets.digest(input.readBytes()))
        val file = requireNotNull(assets.file(key))
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
        assertTrue(bitmap.width <= 2560); assertEquals(2f, bitmap.width.toFloat()/bitmap.height, .01f)
        bitmap.recycle(); input.delete(); assertTrue(file.isFile)
        assertNull(assets.file("../../data.jpg"))
    }
    @Test fun normalizationMatchesVisibleInkAndPreservesLineHeight() {
        val face = Typeface.create("serif", Typeface.NORMAL)
        fun ink(type: Typeface) = Rect().also { Paint().apply { typeface = type; textSize = 1000f }.getTextBounds("国Ag", 0, 3, it) }.height()
        val ratio = AppearanceAssets.heightScale(face)
        assertTrue(ratio in .65f..1.6f)
        assertEquals(ink(Typeface.DEFAULT).toFloat(), ink(face) * ratio, 1f)
    }
    @Test fun persistedSettingsIncludeImportedAssets() {
        val value = ThemeSettings(font = "a".repeat(64)+".ttf", fontName = "字体", background = "b".repeat(64)+".jpg", backgroundDim = .58f)
        val store = ThemeSettingsStore(app); store.save(value); assertEquals(value, store.load())
    }
    @Test fun rejectsNonFontAndBoundedReadNeverReturnsOversize() {
        val file = File(app.cacheDir, "not-font.ttf").apply { writeText("this is not a font file") }
        assertTrue(runCatching { AppearanceAssets(app).importFont(Uri.fromFile(file)) }.isFailure)
        assertTrue(runCatching { AppearanceAssets.readBounded(ByteArray(20).inputStream(), 10) }.isFailure)
    }
}
