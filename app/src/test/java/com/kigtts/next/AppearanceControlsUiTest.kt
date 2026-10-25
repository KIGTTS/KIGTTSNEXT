package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AppearanceControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun themeSourcesAreExclusiveAndBackgroundChoiceKeepsCustomSeed() {
        var state = ThemeSettings(background = "a".repeat(64) + ".jpg", backgroundSeed = 0xff4a8c42)
        compose.setContent { var settings by remember { mutableStateOf(state) }
            KigttsTheme(settings) { Surface { ThemeColorSettings(settings) { settings = it; state = it } } }
        }
        compose.onNodeWithContentDescription("取色：背景图").performClick()
        compose.waitForIdle(); assertEquals(ThemeColorSource.BACKGROUND, state.colorSource)
        compose.onNodeWithText("主题色", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("取色：自定义").performClick()
        compose.waitForIdle(); assertEquals(0xff00696e, state.seed)
        compose.onNodeWithText("主题色").assertExists()
    }
    @Test fun colorPromptCanRetainColorAndRememberDontAsk() {
        var result: Pair<Boolean, Boolean>? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { BackgroundThemePrompt { change, remind -> result = change to remind } } }
        compose.onNodeWithText("下次不再提醒").performClick()
        compose.onNodeWithText("保留当前配色").performClick()
        compose.waitUntil(5000) { result != null }
        assertEquals(false to false, result)
    }
    @Test fun backgroundCropShowsSquareAndCommitsOnlyWhenConfirmed() {
        val assets = AppearanceAssets(compose.activity)
        val input = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val key = assets.saveBackground(input)
        var result: String? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { BackgroundCropScreen(key, {}, { saved, _ -> result = saved }) } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("background-crop-square").fetchSemanticsNodes().isNotEmpty() }
        assertNull(result)
        compose.onRoot().captureRoboImage("build/previews/background-cross-crop.png")
        compose.onNodeWithTag("background-crop-save").performClick()
        compose.waitForIdle()
        compose.waitUntil(10000) { result != null || compose.onAllNodesWithText("背景未能保存，请重试").fetchSemanticsNodes().isNotEmpty() }
        assertNotNull(org.robolectric.shadows.ShadowLog.getLogs().joinToString { it.msg + it.throwable?.stackTraceToString() }, result)
        val bitmap = android.graphics.BitmapFactory.decodeFile(assets.file(result)!!.path)
        assertEquals(bitmap.width, bitmap.height)
    }
}
