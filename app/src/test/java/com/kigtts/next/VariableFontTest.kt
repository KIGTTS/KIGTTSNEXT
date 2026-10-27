package com.kigtts.next

import android.app.Application
import android.graphics.*
import androidx.compose.ui.text.font.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [26, 35])
class VariableFontTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun fixture() = File(app.cacheDir, "variable-fixture.ttf").also { file ->
        javaClass.getResourceAsStream("/fixtures/variable-font/fixture.ttf")!!.use { source -> file.outputStream().use(source::copyTo) }
    }
    @Test fun readsRealRangeAndRejectsStaticOrMalformedTables() {
        assertEquals(FontWeightAxis(200f, 200f, 1000f), readFontWeightAxis(fixture()))
        val static = File(app.cacheDir, "static-fixture.ttf").apply {
            writeBytes(this@VariableFontTest.javaClass.getResourceAsStream("/fixtures/appearance-font/fixture.ttf")!!.use { it.readBytes() })
        }
        assertNull(readFontWeightAxis(static))
        val malformed = File(app.cacheDir, "broken.ttf")
        listOf(byteArrayOf(0, 1, 0, 0), ByteBuffer.allocate(28).putInt(0x00010000).putShort(1).put(ByteArray(6))
            .putInt(0x66766172).putInt(0).putInt(Int.MAX_VALUE).putInt(64).array()).forEach {
            malformed.writeBytes(it); assertNull(readFontWeightAxis(malformed))
        }
        val axis = FontWeightAxis(200f, 400f, 900f)
        assertEquals(200, axis.clamp(1)); assertEquals(900, axis.clamp(1000))
        assertEquals(800f, axis.forStyle(500, 700), 0f)
    }
    @Test fun composeResolvesRealWeightChangesWhileKeepingHeightNormalized() = runBlocking {
        val file = fixture(); val resolver = createFontFamilyResolver(app)
        suspend fun ink(weight: Int, style: FontWeight = FontWeight.Normal): Long {
            val font = loadAppearanceFont(file, weight); resolver.preload(font.family!!)
            val face = resolver.resolve(font.family, style, FontStyle.Normal, FontSynthesis.None).value as android.graphics.Typeface
            val bitmap = Bitmap.createBitmap(640, 96, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawText("Ag KIGTTS", 4f, 76f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 64f * font.heightScale; typeface = face
            })
            val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle(); assertTrue(font.heightScale in .65f..1.6f)
            return pixels.sumOf { Color.alpha(it).toLong() }
        }
        val thin = ink(200); val heavy = ink(900)
        assertTrue("字重必须改变真实字形的墨量", heavy > thin * 1.4)
        assertTrue("原有粗体层级仍然生效", ink(400, FontWeight.Bold) > ink(400))
    }
    @Test fun selectedWeightPersistsAndOlderPreferencesDefaultTo400() {
        val store = ThemeSettingsStore(app)
        store.save(ThemeSettings(fontWeight = 650)); assertEquals(650, store.load().fontWeight)
        app.getSharedPreferences("appearance", 0).edit().remove("fontWeight").commit()
        assertEquals(400, store.load().fontWeight)
    }
}
