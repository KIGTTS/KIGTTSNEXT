package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.cards.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.CameraSettingsScreen
import com.kigtts.next.ui.settings.PhotoWeatherSettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PhotoWeatherSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cameraRowTogglesFromItsLabelAndWeatherEntryOpensItsPage() {
        var opened = false
        compose.setContent { KigttsTheme(ThemeSettings()) { CameraSettingsScreen { opened = true } } }
        compose.onNodeWithContentDescription("拍照时记录位置与天气信息").performScrollTo().assertIsOff().performClick().assertIsOn()
        assertTrue(PhotoServiceSettings(compose.activity).recordCapture)
        compose.onNodeWithText("天气设置").performScrollTo().performClick()
        assertTrue(opened)
        compose.onNodeWithText("清除地点与天气授权").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/camera-facts-settings.png")
    }

    @Test fun firstSourceClickSelectsAndSecondClickConfiguresWithoutAKey() {
        compose.setContent { KigttsTheme(ThemeSettings()) { PhotoWeatherSettingsScreen() } }
        compose.onNodeWithText("高德天气").performScrollTo().performClick()
        assertEquals(PhotoWeatherSource.AMAP, PhotoServiceSettings(compose.activity).weatherSource)
        compose.onNodeWithText("API Key").assertDoesNotExist()
        compose.onNodeWithText("高德天气").performClick()
        compose.onNodeWithText("API Key").performTextInput("test-key")
        compose.onNodeWithText("保存").performClick()
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        assertEquals("test-key", PhotoServiceSettings(compose.activity).key(PhotoWeatherSource.AMAP))
        compose.onNodeWithText("心知天气").performScrollTo().performClick()
        assertEquals(PhotoWeatherSource.SENIVERSE, PhotoServiceSettings(compose.activity).weatherSource)
        compose.onRoot().captureRoboImage("build/previews/photo-weather-settings.png")
    }
}
