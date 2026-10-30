package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.settings.AppearanceAssetSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [29, 35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SecondaryBackgroundUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun otherPageBackgroundIsOptInAndLargeTitleSharesItEvenWhenCollapsed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.rgb(20, 180, 90)) }
        val key = AppearanceAssets(app).saveBackground(source); source.recycle()
        AppearanceBackgrounds.load(app, key, BackgroundBlur.PAGE)
        AppearanceBackgrounds.load(app, key, BackgroundBlur.CARD)
        var settings by mutableStateOf(ThemeSettings(mode = ThemeMode.LIGHT, background = key, backgroundDim = .2f,
            secondaryPageBackground = false))
        compose.setContent {
            KigttsTheme(settings) {
                CompositionLocalProvider(LocalAppAppearanceSettings provides settings) {
                    PageScaffold("外观", {}) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("test-page-scroll")) {
                            AppearanceAssetSettings(settings, { settings = it }, {})
                            Spacer(Modifier.height(1100.dp)); Text("页面底部")
                        }
                    }
                }
            }
        }
        var frame = 0
        fun headerGreen(): Float {
            val path = "build/previews/secondary-header-${frame++}.png"
            compose.onNodeWithTag("large-page-bar").captureRoboImage(path)
            val image = BitmapFactory.decodeFile(path)
            val color = image.getPixel((image.width * .7f).toInt(), (image.height * .2f).toInt())
            image.recycle()
            return (AndroidColor.green(color) - AndroidColor.red(color)) / 255f
        }
        assertTrue(headerGreen() < .1f)
        compose.onNodeWithContentDescription("在其它页面显示背景").performClick()
        compose.runOnIdle { assertTrue(settings.secondaryPageBackground) }
        assertTrue(headerGreen() > .3f)
        compose.onRoot().captureRoboImage("build/previews/secondary-background-light-blur.png")
        compose.onNodeWithTag("test-page-scroll").performTouchInput { swipeUp() }
        assertTrue(headerGreen() > .3f)
    }
}
