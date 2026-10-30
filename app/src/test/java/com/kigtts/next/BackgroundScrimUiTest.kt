package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class BackgroundScrimUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun livePageAndCardPixelsFollowTheUserScrimInsteadOfStackingAnOpaqueSurface() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val source = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.rgb(30, 150, 75)) }
        val key = AppearanceAssets(app).saveBackground(source)
        source.recycle()
        val pixels = BackgroundBlur.entries.associateWith { blur ->
            AppearanceBackgrounds.load(app, key, blur)!!.getPixel(64, 64)
        }
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.LIGHT, background = key,
            secondaryPageBackground = true, backgroundDim = .35f))
        var pageColor = Color.Unspecified
        var cardColor = Color.Unspecified
        compose.setContent { KigttsTheme(settings) {
            pageColor = MaterialTheme.colorScheme.background
            cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
            CompositionLocalProvider(LocalAppAppearanceSettings provides settings) {
                PageScaffold("遮罩", {}) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.fillMaxWidth().height(100.dp).testTag("page-scrim"))
                        AppearanceCardSurface(Modifier.fillMaxWidth().height(100.dp).testTag("card-scrim")) {}
                        AppearanceBackdrop(settings, Modifier.fillMaxWidth().height(100.dp).testTag("home-scrim"))
                    }
                }
            }
        } }
        var frame = 0
        fun check(tag: String, base: Int, scrim: Color, alpha: Float) {
            val path = "build/previews/scrim-${frame++}-$tag.png"
            compose.onRoot().captureRoboImage(path.replace(".png", "-root.png"))
            compose.onNodeWithTag(tag).captureRoboImage(path)
            val image = BitmapFactory.decodeFile(path)
            val actual = image.getPixel(image.width / 2, image.height / 2)
            image.recycle()
            val expected = scrim.copy(alpha = alpha).compositeOver(Color(base)).toArgb()
            listOf<(Int) -> Int>(AndroidColor::red, AndroidColor::green, AndroidColor::blue).forEach { channel ->
                assertEquals("$tag at $alpha", channel(expected).toDouble(), channel(actual).toDouble(), 3.0)
            }
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            listOf(.0f, .35f, .8f).forEach { dim ->
                compose.runOnIdle { settings = settings.copy(mode = mode, backgroundDim = dim) }
                compose.waitForIdle()
                check("page-scrim", pixels.getValue(BackgroundBlur.PAGE), pageColor, 1f - .8f * (1f - dim))
                check("card-scrim", pixels.getValue(BackgroundBlur.CARD), cardColor, 1f - .8f * (1f - dim))
                check("home-scrim", pixels.getValue(BackgroundBlur.NONE), pageColor, dim)
            }
        }
    }
}
