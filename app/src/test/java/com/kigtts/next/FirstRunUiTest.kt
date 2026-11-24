package com.kigtts.next

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.HomePanel
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.ui.onboarding.FirstRunStep
import com.kigtts.next.ui.onboarding.FirstRunStore
import com.kigtts.next.ui.onboarding.HomeTip
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class FirstRunUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guideReadsExistingTermsRemembersItsStepAndEntersHomeWithoutGrantingPermissions() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val guide = FirstRunStore(compose.activity)
        compose.onNodeWithTag("onboarding-agreement").assertIsDisplayed()
        compose.onNodeWithTag("speech-editor").assertDoesNotExist()
        screenshot("agreement")
        compose.onNodeWithText("用户协议").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-permissions").assertIsDisplayed()
        screenshot("permissions")
        assertEquals(FirstRunStep.PERMISSIONS, guide.step())
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("onboarding-permissions").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithContentDescription("主题模式：深色").performScrollTo().performClick()
        compose.onNodeWithContentDescription("主题模式：深色").assertIsSelected()
        assertEquals(ThemeMode.DARK, model.state.value.theme.mode)
        screenshot("appearance")
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-features").assertIsDisplayed()
        screenshot("features")
        compose.onNodeWithText("试试快捷文本").performClick()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        assertEquals(FirstRunStep.COMPLETE, guide.step())
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-tip-panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("知道了").performClick()
        assertTrue(guide.seen(HomeTip.PANEL))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("onboarding-agreement").assertDoesNotExist()
        compose.onNodeWithTag("home-tip-panel").assertDoesNotExist()
    }

    @Test fun cursorAndQuickEditTipsAppearOnUseAndPersistTheirDismissalOrChoice() {
        repeat(4) { compose.onNodeWithTag("onboarding-next").performClick() }
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val guide = FirstRunStore(compose.activity)
        compose.onNodeWithTag("home-tip-cursor").assertDoesNotExist()
        compose.runOnIdle { model.editing(true) }
        compose.onNodeWithTag("home-tip-cursor").assertDoesNotExist()
        compose.runOnIdle { model.text("今天想试试光标横条") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-tip-cursor").fetchSemanticsNodes().isNotEmpty() }
        screenshot("cursor-tip")
        assertTrue(guide.seen(HomeTip.CURSOR))
        compose.onNodeWithText("知道了").performClick()
        compose.runOnIdle { model.editing(false) }
        compose.runOnIdle { model.editing(true) }
        compose.onNodeWithTag("home-tip-cursor").assertDoesNotExist()
        compose.runOnIdle { model.editing(false); model.editQuickTexts() }
        compose.onNodeWithTag("quick-edit-done").assertIsDisplayed()
        compose.onNodeWithTag("home-tip-quick_edit").assertDoesNotExist()
        compose.onNodeWithTag("quick-edit-done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-tip-quick_edit").fetchSemanticsNodes().isNotEmpty() }
        screenshot("quick-edit-tip")
        compose.onNodeWithText("开启").performClick()
        assertEquals(true, model.quickSort.enabled.value)
        assertTrue(guide.seen(HomeTip.QUICK_EDIT))
        compose.runOnIdle { model.editQuickTexts() }
        compose.onNodeWithTag("quick-edit-done").performClick()
        compose.onNodeWithTag("home-tip-quick_edit").assertDoesNotExist()
    }

    private fun screenshot(step: String) = compose.onRoot().captureRoboImage("build/previews/onboarding-$step.png")
}
