package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.settings.AppearanceSettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AppearanceScrollOwnershipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun touchingContentDuringHandleSettlingStopsTheOldAnimationImmediately() {
        compose.setContent {
            KigttsTheme(ThemeSettings()) { PageScaffold("外观", {}, contentScrollsHeader = false) {
                AppearanceSettingsScreen(ThemeSettings(), {})
            } }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val handle = compose.onNodeWithTag("appearance-sheet-handle", useUnmergedTree = true)
        val sheet = compose.onNodeWithTag("appearance-settings-sheet")
        handle.performTouchInput { swipe(center, Offset(centerX, -250f), 180) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(48)
        sheet.performTouchInput { down(Offset(width * .9f, 20f)) }
        compose.waitForIdle()
        val stoppedTop = sheet.getUnclippedBoundsInRoot().top.value
        compose.mainClock.advanceTimeBy(400)
        assertEquals("新触摸期间抽屉不能继续被旧归位动画拉走", stoppedTop,
            sheet.getUnclippedBoundsInRoot().top.value, .5f)
        sheet.performTouchInput { moveBy(Offset(0f, 80f)); up() }
        compose.mainClock.advanceTimeBy(400)
        sheet.assertExists()
    }

    @Test fun sheetAndContentScrollIndependentlyOfHeaderWhileHeaderStillAcceptsDirectDrag() {
        compose.setContent {
            var settings by remember { mutableStateOf(ThemeSettings()) }
            KigttsTheme(settings) { PageScaffold("外观", {}, contentScrollsHeader = false) {
                AppearanceSettingsScreen(settings, { settings = it })
            } }
        }
        compose.waitForIdle()
        fun headerHeight() = compose.onNodeWithTag("large-page-bar").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val expanded = headerHeight()
        val handle = compose.onNodeWithTag("appearance-sheet-handle", useUnmergedTree = true)
        val sheet = compose.onNodeWithTag("appearance-settings-sheet")
        val initialTop = sheet.getUnclippedBoundsInRoot().top.value
        handle.performTouchInput { swipe(center, Offset(centerX, -250f), 600) }
        compose.waitForIdle()
        assertEquals("抽屉不应同时缩起顶栏", expanded, headerHeight(), .5f)
        assertTrue(sheet.getUnclippedBoundsInRoot().top.value < initialTop - 30f)
        repeat(3) { sheet.performTouchInput { swipeUp(durationMillis = 500) } }
        compose.onNodeWithText("恢复默认外观").assertIsDisplayed()
        assertEquals("内容滚动不应再次驱动顶栏", expanded, headerHeight(), .5f)
        repeat(3) { sheet.performTouchInput { swipeDown(durationMillis = 500) } }
        handle.performTouchInput { swipe(center, Offset(centerX, centerY + 280f), 500) }
        compose.waitForIdle()
        assertEquals(expanded, headerHeight(), .5f)
        compose.onNodeWithTag("large-page-bar").performTouchInput {
            swipe(Offset(width * .7f, height * .85f), Offset(width * .7f, 1f), 500)
        }
        compose.waitForIdle()
        assertTrue("直接拖动顶栏仍能收起", headerHeight() < expanded - 30f)
        handle.performTouchInput { swipe(center, Offset(centerX, -250f), 600) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/previews/appearance-scroll-ownership.png")
        sheet.performTouchInput { swipeUp(durationMillis = 500) }
        compose.onNodeWithText("主题与配色").assertExists()
    }
}
