package com.kigtts.next

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
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

    @Test fun guideSwipesBackThroughTheAgreementAndFinishesOnPermissions() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val guide = FirstRunStore(compose.activity)
        compose.onNodeWithTag("onboarding-welcome").assertIsDisplayed()
        compose.onNodeWithTag("speech-editor").assertDoesNotExist()
        swipeForward()
        awaitStep(guide, FirstRunStep.AGREEMENT)
        compose.onNodeWithTag("onboarding-agreement").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitStep(guide, FirstRunStep.WELCOME)
        compose.onNodeWithTag("onboarding-welcome").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.AGREEMENT)
        swipeForward()
        compose.onNodeWithTag("onboarding-agreement").assertIsDisplayed()
        assertFalse(guide.agreementAccepted())
        compose.onNodeWithText("隐私政策").assertDoesNotExist()
        screenshot("agreement")
        compose.onNodeWithText("用户协议").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.APPEARANCE)
        compose.onNodeWithTag("onboarding-appearance").assertIsDisplayed()
        assertEquals(FirstRunStep.APPEARANCE, guide.step())
        assertTrue(guide.agreementAccepted())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("onboarding-appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription("主题模式：深色").performScrollTo().performClick()
        compose.onNodeWithContentDescription("主题模式：深色").assertIsSelected()
        assertEquals(ThemeMode.DARK, model.state.value.theme.mode)
        screenshot("appearance")
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.FEATURES)
        compose.onNodeWithTag("onboarding-features").assertIsDisplayed()
        screenshot("features")
        swipeForward()
        awaitStep(guide, FirstRunStep.PERMISSIONS)
        compose.onNodeWithTag("onboarding-permissions").assertIsDisplayed()
        assertEquals(FirstRunStep.PERMISSIONS, guide.step())
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        compose.onNodeWithText("隐私政策").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.mainClock.advanceTimeBy(350)
        screenshot("permissions")
        compose.onNodeWithText("试试快捷文本").performClick()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        assertEquals(FirstRunStep.COMPLETE, guide.step())
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-tip-panel").fetchSemanticsNodes().isNotEmpty() }
        assertTipClearsAnchor("home-tip-panel", "sheet-handle")
        compose.onNodeWithText("知道了").performClick()
        assertTrue(guide.seen(HomeTip.PANEL))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("onboarding-agreement").assertDoesNotExist()
        compose.onNodeWithTag("home-tip-panel").assertDoesNotExist()
    }

    @Test fun cursorAndQuickEditTipsAppearOnUseAndPersistTheirDismissalOrChoice() {
        val guide = FirstRunStore(compose.activity)
        FirstRunStep.entries.drop(1).forEach { target ->
            compose.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
            awaitStep(guide, target)
        }
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.onNodeWithTag("home-tip-cursor").assertDoesNotExist()
        compose.runOnIdle { model.editing(true) }
        compose.onNodeWithTag("home-tip-cursor").assertDoesNotExist()
        compose.runOnIdle { model.text("今天想试试光标横条") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-tip-cursor").fetchSemanticsNodes().isNotEmpty() }
        assertTipClearsAnchor("home-tip-cursor", "cursor-track")
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
        assertTipClearsAnchor("home-tip-quick_edit", "dock-start")
        screenshot("quick-edit-tip")
        compose.onNodeWithText("开启").performClick()
        assertEquals(true, model.quickSort.enabled.value)
        assertTrue(guide.seen(HomeTip.QUICK_EDIT))
        compose.runOnIdle { model.editQuickTexts() }
        compose.onNodeWithTag("quick-edit-done").performClick()
        compose.onNodeWithTag("home-tip-quick_edit").assertDoesNotExist()
    }

    private fun assertTipClearsAnchor(tipTag: String, anchorTag: String) {
        val tip = compose.onNodeWithTag(tipTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val anchor = compose.onNodeWithTag(anchorTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertFalse("Tip ${tipTag} at ${tip} overlaps ${anchorTag} at ${anchor}", tip.overlaps(anchor))
    }

    private fun screenshot(step: String) = compose.onRoot().captureRoboImage("build/previews/onboarding-$step.png")

    private fun swipeForward() = compose.onNodeWithTag("onboarding-pages").performTouchInput {
        swipe(
            start = Offset(width * 0.85f, height * 0.93f),
            end = Offset(width * 0.15f, height * 0.93f),
            durationMillis = 370,
        )
    }

    private fun awaitStep(guide: FirstRunStore, step: FirstRunStep) {
        compose.waitUntil("onboarding advances to $step", 5_000) { guide.step() == step }
    }
}
