package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.AppearanceSettingsScreen
import com.kigtts.next.ui.settings.AppearanceHomePreview
import com.kigtts.next.ui.home.HomePreviewGeometry
import com.kigtts.next.ui.home.LocalHomePreviewGeometry
import com.kigtts.next.state.SubtitleAppearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AppearancePreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun allFourCornersUseHostControlPaletteAndBottomTabsRemainVisible() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val geometry = HomePreviewGeometry().apply {
            update("viewport", Rect(0f, 0f, 1179f, 2556f))
            update("subtitle", Rect(36f, 192f, 1143f, 1920f))
            update("phrases", Rect(36f, 1956f, 1143f, 2532f))
        }
        compose.setContent {
            val settings = ThemeSettings(mode = mode.value)
            KigttsTheme(settings) { CompositionLocalProvider(LocalHomePreviewGeometry provides geometry) {
                AppearanceHomePreview(settings, "私有文字不应进入预览", SubtitleAppearance(), Modifier.fillMaxSize())
            } }
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { theme ->
            compose.runOnIdle { mode.value = theme }
            compose.waitForIdle()
            val path = "build/previews/appearance-control-" + theme.name.lowercase() + ".png"
            compose.onNodeWithTag("appearance-home-wireframe").captureRoboImage(path)
            val bitmap = android.graphics.BitmapFactory.decodeFile(path)
            fun color(x: Float, y: Float) = bitmap.getPixel((x / 1179 * bitmap.width).toInt(), (y / 2556 * bitmap.height).toInt())
            val settings = ThemeSettings(mode = theme)
            val palette = themeColors(settings, isDark = theme == ThemeMode.DARK)
            val control = compactControlPalette(settings, dark = theme == ThemeMode.DARK, base = palette).container.toArgb()
            listOf(108f to 264f, 1071f to 264f, 108f to 1848f, 1071f to 1848f).forEach { (x, y) ->
                assertEquals("四角控件应与主界面同色：" + theme, control, color(x, y))
            }
            assertEquals("底部选中 Tab 保留主题色实心胶囊", palette.primaryContainer.toArgb(), color(240f, 2460f))
            assertEquals("底部 Tab 内部不保留图标圆点", palette.primaryContainer.toArgb(), color(108f, 2460f))
            bitmap.recycle()
        }
    }
    @Test fun previewAndNestedCardsRemainVisibleAsSheetExpands() {
        var fonts = false
        compose.setContent {
            var settings by remember { mutableStateOf(ThemeSettings()) }
            KigttsTheme(settings) { Surface(Modifier.fillMaxSize()) { AppearanceSettingsScreen(settings, { settings = it }, onFonts = { fonts = true }) } }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.onNodeWithText("背景与字体").assertIsDisplayed()
        compose.onNodeWithTag("appearance-home-wireframe").assertIsDisplayed()
        compose.onNodeWithContentDescription("主界面预览，示例文字：你好").assertIsDisplayed()
        compose.onAllNodes(hasText("点击输入文字") and hasAnyAncestor(hasTestTag("appearance-preview"))).assertCountEquals(0)
        compose.onNodeWithText("字体").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(fonts) }
        compose.onRoot().captureRoboImage("build/previews/appearance-preview-sheet.png")
        compose.onNodeWithTag("appearance-settings-sheet").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("主题与配色").assertExists()
    }
}
