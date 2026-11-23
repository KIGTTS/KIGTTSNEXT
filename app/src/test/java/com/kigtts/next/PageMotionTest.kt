package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PageMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(compact: Boolean = false, statusGuard: Boolean = false): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model ->
            compose.setContent {
                if (statusGuard) Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFFCFECEE)).testTag("test-system-status"))
                    Box(Modifier.weight(1f)) { KigttsApp(model) }
                } else if (compact) Box(Modifier.height(600.dp)) { KigttsApp(model) } else KigttsApp(model)
            }
            compose.waitForIdle()
        }
    }
    private fun layer(identity: String) = compose.onNodeWithTag("page-layer/" + identity)
    private fun left(identity: String) = layer(identity).getUnclippedBoundsInRoot().left.value
    private fun step(time: Long) { compose.mainClock.advanceTimeBy(time); compose.waitForIdle() }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    private fun predict(value: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, value, edge))
        }
        step(64)
    }
    @Test fun secondaryPageLayersLeaveTheStatusBarUnshadedAtRestAndDuringTransitions() {
        val model = show(statusGuard = true)
        fun clearStatus(name: String) {
            val path = "build/previews/" + name + ".png"
            compose.onRoot().captureRoboImage(path)
            val bitmap = BitmapFactory.decodeFile(path)
            val strip = compose.onNodeWithTag("test-system-status").getUnclippedBoundsInRoot()
            val density = compose.activity.resources.displayMetrics.density
            val row = (strip.bottom.value * density).toInt() - 1
            listOf(.1f, .5f, .9f).forEach { x -> assertEquals(Color(0xFFCFECEE).toArgb(), bitmap.getPixel((bitmap.width * x).toInt(), row)) }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.navigate(Destination.Settings) }; step(96)
        clearStatus("navigation-status-enter-unshaded")
        step(1500)
        clearStatus("navigation-status-idle-unshaded")
        predict(.7f)
        clearStatus("navigation-status-predictive-unshaded")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }; step(96)
        clearStatus("navigation-status-return-unshaded")
        step(1500); compose.mainClock.autoAdvance = true
    }
    @Test fun leavingFocusedPagesClearsInputAndParentScrollSurvivesAfterPickerCancellation() {
        val model = show(compact = true)
        compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        compose.onNodeWithTag("custom-theme-color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("HEX 颜色").performTextReplacement("#123456")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("恢复默认外观").performScrollTo()
        val scrolling = SemanticsMatcher("vertical scroll") { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) } and
            hasAnyAncestor(hasTestTag("navigation-secondary"))
        val position = compose.onNode(scrolling).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(position > 0f)
        compose.runOnIdle { model.navigate(Destination.Plugins) }
        compose.waitForIdle()
        compose.onAllNodes(isFocused(), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("返回上一页").performClick()
        compose.waitForIdle()
        val restored = compose.onNode(scrolling).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(position, restored, .5f)
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0xFF00696E,model.state.value.theme.seed) }
        capture("navigation-parent-scroll-restored")
    }
    @Test fun secondAndThirdLevelEnterAndToolbarReturnRetainMovingPagesUntilFinished() {
        val model = show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.navigate(Destination.Settings) }
        step(96)
        assertTrue(left("1/settings") > 1f && left("1/settings") < 390f)
        assertTrue(left("0/home") < 0f)
        capture("navigation-second-enter-mid")
        step(1500)
        assertEquals(0f, left("1/settings"), .5f)
        compose.runOnIdle { model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        step(96)
        assertTrue(left("2/settings/APPEARANCE") > 1f)
        assertTrue(left("1/settings") < 0f)
        capture("navigation-third-enter-mid")
        step(1500)
        compose.onNodeWithContentDescription("返回上一页").performClick()
        step(96)
        assertEquals(Destination.Settings, model.state.value.destination)
        assertTrue(left("2/settings/APPEARANCE") > 1f)
        assertTrue(left("1/settings") < 0f)
        capture("navigation-third-return-mid")
        step(1500)
        layer("2/settings/APPEARANCE").assertDoesNotExist()
        assertEquals(0f, left("1/settings"), .5f)
        compose.onNodeWithContentDescription("返回上一页").performClick()
        step(96)
        layer("1/settings").assertExists()
        capture("navigation-second-return-mid")
        step(1500); compose.mainClock.autoAdvance = true
        layer("1/settings").assertDoesNotExist()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
    }
    @Test fun predictiveCommitContinuesFromPoseAndCancelRestoresTheWholeParent() {
        val model = show()
        compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        predict(.72f)
        val before = left("2/settings/APPEARANCE")
        val parentBefore = left("1/settings")
        capture("navigation-predictive-before-commit")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        val seeded = left("2/settings/APPEARANCE")
        assertTrue("No jump at commit: " + before + " -> " + seeded, abs(seeded - before) < 2f)
        assertTrue("Parent keeps its preview pose", abs(left("1/settings") - parentBefore) < 2f)
        step(96)
        assertTrue(left("2/settings/APPEARANCE") > seeded + 20f)
        capture("navigation-predictive-commit-mid")
        step(1500)
        layer("2/settings/APPEARANCE").assertDoesNotExist()
        assertEquals(Destination.Settings, model.state.value.destination)
        val normal = layer("1/settings").getUnclippedBoundsInRoot()
        predict(.6f, BackEventCompat.EDGE_RIGHT)
        capture("navigation-predictive-cancel-before")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        step(1500); compose.mainClock.autoAdvance = true
        assertEquals(normal, layer("1/settings").getUnclippedBoundsInRoot())
        assertEquals(Destination.Settings, model.state.value.destination)
    }
    @Test fun interruptedTransitionsRepeatedDestinationsAndFastBackDoNotLoseHomeState() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        compose.onNodeWithTag("editor-font-size").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.navigate(Destination.Settings) }
        step(64)
        compose.runOnIdle { model.navigate(Destination.Plugins) }
        step(48)
        compose.runOnIdle { model.navigate(Destination.Settings) }
        step(48)
        layer("1/settings").assertExists(); layer("3/settings").assertExists()
        capture("navigation-repeated-route-mid")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        step(16)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        step(16)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        step(16)
        assertEquals(Destination.Home, model.state.value.destination)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        capture("navigation-fast-return-home-mid")
        step(1600); compose.mainClock.autoAdvance = true
        layer("1/settings").assertDoesNotExist(); layer("2/plugins").assertDoesNotExist(); layer("3/settings").assertDoesNotExist()
        assertEquals(340f, model.state.value.panelHeight, .1f)
        compose.onNodeWithContentDescription("字幕字号滑条").assertIsDisplayed()
        // Returning and reopening during an exit revives the same page without a duplicate saved-state key.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.navigate(Destination.Settings) }; step(1600)
        compose.runOnIdle { model.navigateBack() }; step(48)
        val retiring = left("1/settings")
        compose.runOnIdle { model.navigate(Destination.Settings) }
        assertTrue(abs(left("1/settings") - retiring) < 2f)
        step(1600); compose.mainClock.autoAdvance = true
        assertEquals(0f, left("1/settings"), .5f)
    }
    @Test fun sketchAndCardsHaveFullEntryReturnAndPredictiveCompletionFromEitherSide() {
        val model = show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.openSidePage(SidePage.SKETCH) }; step(96)
        assertTrue(left("1/side/sketch") < -1f)
        capture("navigation-sketch-enter-mid")
        step(1500)
        predict(.6f, BackEventCompat.EDGE_LEFT)
        assertTrue("Sketch follows its left entry even for a left-edge back gesture", left("1/side/sketch") < -1f)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }; step(1500)
        compose.onNodeWithContentDescription("返回大字幕").performClick(); step(96)
        assertNull(model.state.value.sidePage)
        assertTrue(left("1/side/sketch") < -1f)
        capture("navigation-sketch-return-mid")
        step(1500)
        layer("1/side/sketch").assertDoesNotExist()
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }; step(96)
        assertTrue(left("1/cards") > 1f)
        capture("navigation-cards-enter-mid")
        step(1500)
        predict(.55f, BackEventCompat.EDGE_RIGHT)
        val before = left("1/cards")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrue(abs(left("1/cards") - before) < 2f)
        step(96)
        assertTrue(left("1/cards") > before + 20f)
        capture("navigation-cards-predictive-return-mid")
        step(1500); compose.mainClock.autoAdvance = true
        layer("1/cards").assertDoesNotExist()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
    }
}
