package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.plugin.api.UiSlot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ButtonFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val feedback = mutableListOf<HapticFeedbackType>()
    private fun show(count: Int = 0): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        app.getSharedPreferences("ui-debug", 0).edit().putInt(UiSlot.EDITOR_TOP_END.name, count).commit()
        val native = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback.add(hapticFeedbackType) }
        }
        return testAppModel(app).also { model ->
            compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { KigttsApp(model) } }
        }
    }
    private fun tick(type: HapticFeedbackType = HapticFeedbackType.VirtualKey, action: () -> Unit) {
        val before = feedback.size
        action(); compose.waitForIdle()
        assertEquals(listOf(type), feedback.drop(before))
    }

    @Test fun hostPluginOverflowMenuAndPageButtonsHaveExactlyOneTick() {
        val model = show(20)
        compose.onRoot().captureRoboImage("build/previews/feedback-home.png")
        tick {
            compose.onNodeWithTag("editor-flip").performTouchInput { click() }
            compose.runOnIdle { assertTrue("Physical tap must flip the subtitle", model.state.value.subtitleFlipped) }
        }
        tick { compose.onNodeWithTag("editor-top-end-more").performClick() }
        tick { compose.onNodeWithTag("debug.ui/EDITOR_TOP_END/19-menu").performScrollTo().performClick() }
        tick { compose.onNodeWithText("显示对话框").performClick() }
        tick { compose.onNodeWithText("知道了").performClick() }
        tick { compose.onNodeWithContentDescription("打开抽屉菜单").performClick() }
        tick { compose.onNodeWithText("设置").performClick() }
        tick { compose.onNodeWithText("插件管理").performClick() }
        tick { compose.onNodeWithContentDescription("返回上一页").performClick() }
    }

    @Test fun dockTabsToolsAndDialogControlsShareFeedback() {
        val model = show()
        compose.onRoot().captureRoboImage("build/previews/feedback-dock.png")
        tick {
            compose.onNodeWithTag("dock-main").performTouchInput { click() }
            compose.runOnIdle { assertEquals("Physical tap must open the quick sheet", HomePanel.QUICK_TEXT, model.state.value.panel) }
        }
        tick { compose.onNodeWithTag("category-生活").performClick() }
        tick { compose.onNodeWithContentDescription("收起快捷文本").performClick() }
        tick { compose.onNodeWithContentDescription("画板").performClick() }
        tick(HapticFeedbackType.ToggleOn) { compose.onNodeWithContentDescription("橡皮擦").performClick() }
        tick { compose.onNodeWithContentDescription("清空画板").performClick() }
        tick { compose.onNodeWithText("取消").performClick() }
        tick { compose.onNodeWithContentDescription("返回大字幕").performClick() }
        compose.runOnIdle { model.invoke("input.voice", "toggle") }
        tick(HapticFeedbackType.LongPress) { compose.onNodeWithTag("dock-main").performTouchInput { longClick() } }
    }

    @Test fun cancelledPressesScrollingAndDisabledButtonsDoNotBuzz() {
        val model = show()
        compose.onNodeWithTag("editor-flip").performTouchInput { down(center); moveTo(Offset(center.x + 180f, center.y)); up() }
        compose.runOnIdle { assertTrue(feedback.isEmpty()); assertFalse(model.state.value.subtitleFlipped) }
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        compose.onNodeWithTag("phrases-vertical").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(feedback.isEmpty()) }
        compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        tick(HapticFeedbackType.ToggleOn) { compose.onNodeWithContentDescription("壁纸取色").performClick() }
        val before = feedback.size
        compose.onNodeWithContentDescription("主题色 #6750A4").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(before, feedback.size) }
    }

    @Test fun globalPreferenceGatesNativeLongPressAndPluginActionsAndCanBeRestored() {
        val model = show(20)
        compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.FEEDBACK)) }
        tick(HapticFeedbackType.ToggleOff) { compose.onNodeWithContentDescription("按键震动反馈").performClick() }
        val before = feedback.size
        compose.onNodeWithContentDescription("返回上一页").performClick()
        compose.onNodeWithContentDescription("返回上一页").performClick()
        compose.onNodeWithTag("editor-top-end-more").performClick()
        compose.onNodeWithTag("debug.ui/EDITOR_TOP_END/19-menu").performScrollTo().performClick()
        compose.onNodeWithText("显示对话框").performClick()
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithTag("editor-area").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(before, feedback.size); assertTrue(model.state.value.forcedFullscreen) }
        compose.runOnIdle { model.exitSubtitleFullscreen(); model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.FEEDBACK)) }
        compose.onNodeWithContentDescription("按键震动反馈").performClick()
        compose.runOnIdle { assertEquals(before, feedback.size); assertTrue(model.state.value.hapticsEnabled) }
        tick { compose.onNodeWithContentDescription("返回上一页").performClick() }
    }
}
