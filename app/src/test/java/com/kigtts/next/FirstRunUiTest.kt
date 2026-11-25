package com.kigtts.next

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class FirstRunUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guideIgnoresSwipesAndRequiresConsentOnTheLastPage() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val guide = FirstRunStore(compose.activity)
        compose.onNodeWithTag("onboarding-welcome").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-welcome-wordmark").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-brand").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-progress").assertDoesNotExist()
        val pageTop = compose.onNodeWithTag("onboarding-pages").fetchSemanticsNode().boundsInRoot.top
        val copy = compose.onNodeWithTag("onboarding-welcome-copy").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val art = compose.onNodeWithTag("onboarding-welcome-art").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("onboarding-next").fetchSemanticsNode().boundsInRoot
        assertTrue(copy.bottom < art.top)
        assertTrue(copy.left < art.left)
        assertEquals(copy.right, art.right, 1f)
        assertTrue(art.bottom <= next.top)
        assertEquals(1576f / 1200f, art.width / art.height, .01f)
        compose.onNodeWithTag("speech-editor").assertDoesNotExist()
        swipeForward()
        awaitStep(guide, FirstRunStep.WELCOME)
        val clover = compose.onNodeWithTag("onboarding-clover")
        val welcomeFlowerTop = clover.fetchSemanticsNode().boundsInRoot.top
        assertTrue("The welcome flower clears the wordmark and copy", welcomeFlowerTop >= copy.bottom)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.mainClock.advanceTimeBy(160)
        val movingFlowerTop = clover.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = true
        awaitStep(guide, FirstRunStep.APPEARANCE)
        val appearanceFlowerTop = clover.fetchSemanticsNode().boundsInRoot.top
        assertTrue(movingFlowerTop < welcomeFlowerTop)
        assertTrue(movingFlowerTop > appearanceFlowerTop)
        compose.onNodeWithTag("onboarding-appearance").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-brand").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-progress").assertIsDisplayed()
        assertEquals(pageTop, compose.onNodeWithTag("onboarding-pages").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitStep(guide, FirstRunStep.WELCOME)
        compose.onNodeWithTag("onboarding-welcome").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-brand").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.APPEARANCE)
        assertFalse(guide.agreementAccepted())
        compose.onNodeWithText("用户协议").assertDoesNotExist()
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
        awaitStep(guide, FirstRunStep.FEATURES)
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.PERMISSIONS)
        compose.onNodeWithTag("onboarding-permissions").assertIsDisplayed()
        compose.onNodeWithTag("speech-editor").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        compose.onNodeWithText("试试快捷文本").assertIsNotEnabled()
        assertFalse(guide.finish(agreementConfirmed = false))
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        for (document in listOf("隐私政策", "用户协议")) {
            compose.onNodeWithText(document).performScrollTo().performClick()
            compose.onNode(isDialog()).assertIsDisplayed()
            compose.onNodeWithText("关闭").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("onboarding-consent").performScrollTo().assertIsOff().performClick().assertIsOn()
        screenshot("permissions")
        compose.onNodeWithText("试试快捷文本").performClick()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        assertEquals(FirstRunStep.COMPLETE, guide.step())
        assertTrue(guide.agreementAccepted())
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-tip-panel").fetchSemanticsNodes().isNotEmpty() }
        assertTipClearsAnchor("home-tip-panel", "sheet-handle")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("知道了").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("home-tip-panel").assertExists()
        compose.mainClock.advanceTimeBy(160)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("home-tip-panel").assertDoesNotExist()
        assertTrue(guide.seen(HomeTip.PANEL))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("onboarding-permissions").assertDoesNotExist()
        compose.onNodeWithTag("home-tip-panel").assertDoesNotExist()
    }

    @Test fun appearanceStepSelectsCropsAndStoresABackground() {
        val guide = FirstRunStore(compose.activity)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.runOnIdle { model.theme(model.state.value.theme.copy(askBackgroundColor = false)) }
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.APPEARANCE)

        val source = File(compose.activity.cacheDir, "onboarding-wallpaper.png")
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff12858b.toInt())
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.onNodeWithTag("appearance-background-image").performScrollTo().performClick()
        var pending: org.robolectric.shadows.ShadowActivity.IntentForResult? = null
        compose.waitUntil(5_000) {
            pending = shadowOf(compose.activity).nextStartedActivityForResult
            pending != null
        }
        val request = requireNotNull(pending)
        assertEquals(Intent.ACTION_GET_CONTENT, request.intent.action)
        assertEquals("image/*", request.intent.type)
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(source)))
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("background-crop-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("background-crop-save").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("background-crop-square").fetchSemanticsNodes().isEmpty() }
        val key = requireNotNull(model.state.value.theme.background)
        assertNotNull(model.state.value.theme.backgroundSeed)
        assertEquals(FirstRunStep.APPEARANCE, guide.step())
        assertNotNull(com.kigtts.next.theme.AppearanceAssets(compose.activity).file(key))
        compose.onNodeWithContentDescription("移除背景图片").performScrollTo().performClick()
        assertNull(model.state.value.theme.background)
    }

    @Test fun cursorAndQuickEditTipsAppearOnUseAndPersistTheirDismissalOrChoice() {
        val guide = FirstRunStore(compose.activity)
        FirstRunStep.entries.drop(1).dropLast(1).forEach { target ->
            compose.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
            awaitStep(guide, target)
        }
        compose.onNodeWithTag("onboarding-consent").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        awaitStep(guide, FirstRunStep.COMPLETE)
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
        compose.waitForIdle()
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
