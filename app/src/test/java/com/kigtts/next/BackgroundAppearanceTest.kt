package com.kigtts.next

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundAppearanceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Test fun secondaryPagesUseAStrongerScrimWithoutChangingHomeOrExceedingOpaque() {
        listOf(0f, .2f, .35f, .7f, 1f).forEach { dim ->
            assertEquals(dim, appearanceBackgroundDim(dim, BackgroundBlur.NONE), .001f)
            assertEquals(1f - .8f * (1f - dim), appearanceBackgroundDim(dim, BackgroundBlur.PAGE), .001f)
            assertEquals(1f - .8f * (1f - dim), appearanceBackgroundDim(dim, BackgroundBlur.CARD), .001f)
        }
        assertEquals(.48f, appearanceBackgroundDim(.35f, BackgroundBlur.PAGE), .001f)
    }
    @Test fun squareCropClampsPanAndRetainsOriginalPixels() {
        val image = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        Canvas(image).apply { drawColor(Color.GREEN); drawRect(0f, 0f, 100f, 300f, Paint().apply { color = Color.RED }) }
        val crop = BackgroundCrop().move(600, 300, 300f, 900f, 900f, 2f)
        val bounds = crop.bounds(600, 300)
        assertEquals(0f, bounds.left, .01f); assertEquals(0f, bounds.top, .01f)
        val result = cropBackground(image, crop)
        assertEquals(150, result.width); assertEquals(result.width, result.height)
        assertEquals(Color.RED, result.getPixel(20, 20)); assertEquals(Color.GREEN, image.getPixel(200, 100))
        assertEquals(Color.GREEN, cropBackground(image, BackgroundCrop()).getPixel(150, 150))
    }
    @Test fun gaussianIsSymmetricAndCachedWithoutTouchingSource() {
        val image = Bitmap.createBitmap(65, 65, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK); setPixel(32, 32, Color.WHITE) }
        val output = gaussianBlur(image, 3f)
        // Final 8-bit dither can differ by up to two levels between mirrored pixels.
        assertEquals(Color.red(output.getPixel(30, 32)).toDouble(), Color.red(output.getPixel(34, 32)).toDouble(), 2.0)
        assertEquals(Color.red(output.getPixel(32, 30)).toDouble(), Color.red(output.getPixel(32, 34)).toDouble(), 2.0)
        assertTrue(Color.red(output.getPixel(32, 32)) < 255)
        assertTrue(Color.red(output.getPixel(31, 32)) > 0)
        assertEquals(Color.WHITE, image.getPixel(32, 32))
        val assets = AppearanceAssets(app)
        val key = assets.saveBackground(image)
        val raw = assets.file(key)!!.readBytes()
        val cache = BackgroundBlurCache(app)
        val file = cache.file(key)!!; val time = file.lastModified()
        assertEquals(file, cache.file(key)); assertEquals(time, file.lastModified())
        assertArrayEquals(raw, assets.file(key)!!.readBytes())
        val decoded = AppearanceBackgrounds.load(app, key)!!
        assertSame(decoded, AppearanceBackgrounds.peek(app, key))
        assertSame(decoded, AppearanceBackgrounds.load(app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
            orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }), key))
    }
    @Test fun backgroundPaletteAndDontAskSurviveRestartWithoutReplacingCustomColor() {
        val source = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 170, 80)) }
        val seed = backgroundThemeSeed(source)
        assertTrue(Color.green(seed.toInt()) > Color.red(seed.toInt()))
        val settings = ThemeSettings(seed = 0xff6750a4, background = AppearanceAssets(app).saveBackground(source),
            backgroundSeed = seed, askBackgroundColor = false).withColorSource(ThemeColorSource.BACKGROUND)
        val store = ThemeSettingsStore(app); store.save(settings)
        assertEquals(settings, store.load()); assertFalse(store.load().askBackgroundColor)
        assertEquals(seed, settings.effectiveSeed)
        assertNotEquals(themeColors(settings).primary, themeColors(settings.withColorSource(ThemeColorSource.CUSTOM)).primary)
        assertEquals(0xff6750a4, settings.withColorSource(ThemeColorSource.CUSTOM).effectiveSeed)
        assertEquals(ThemeColorSource.WALLPAPER, settings.withColorSource(ThemeColorSource.WALLPAPER).colorSource)
    }
    @Test fun secondaryBackgroundDefaultsOnAndExplicitOptOutSurvivesRestart() {
        app.getSharedPreferences("appearance", 0).edit().clear().commit()
        val store = ThemeSettingsStore(app)
        assertTrue(ThemeSettings().secondaryPageBackground)
        assertTrue(store.load().secondaryPageBackground)
        val image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        Canvas(image).drawRect(64f, 0f, 128f, 128f, Paint().apply { color = Color.WHITE })
        val key = AppearanceAssets(app).saveBackground(image)
        val cache = BackgroundBlurCache(app)
        val lightFile = cache.file(key, BackgroundBlur.PAGE)!!
        val strongFile = cache.file(key, BackgroundBlur.CARD)!!
        assertNotEquals(lightFile, strongFile)
        val light = BitmapFactory.decodeFile(lightFile.path)
        val strong = BitmapFactory.decodeFile(strongFile.path)
        fun contrast(bitmap: Bitmap) = (60..68).map { y ->
            Color.red(bitmap.getPixel(72, y)) - Color.red(bitmap.getPixel(55, y))
        }.average()
        assertTrue("卡片层的边界过渡应比页面层更柔和", contrast(light) > contrast(strong) + 20)
        val settings = ThemeSettings(background = key, secondaryPageBackground = true)
        store.save(settings); assertEquals(settings, ThemeSettingsStore(app).load())
        store.save(settings.copy(secondaryPageBackground = false))
        assertEquals(key, store.load().background); assertFalse(store.load().secondaryPageBackground)
        image.recycle(); light.recycle(); strong.recycle()
    }
    @Test fun scrollingCardUsesSameScreenCoordinatesAsPageInBothOrientations() {
        val image = androidx.compose.ui.geometry.Size(384f, 384f)
        listOf(androidx.compose.ui.geometry.Size(393f, 852f), androidx.compose.ui.geometry.Size(852f, 393f)).forEach { viewport ->
            val page = backgroundImageRect(image, viewport, androidx.compose.ui.geometry.Offset.Zero)
            listOf(120f, 350f, -80f).forEach { scroll ->
                val origin = androidx.compose.ui.geometry.Offset(24f, scroll)
                val card = backgroundImageRect(image, viewport, origin)
                assertEquals(page, card.translate(origin))
            }
        }
    }
}
