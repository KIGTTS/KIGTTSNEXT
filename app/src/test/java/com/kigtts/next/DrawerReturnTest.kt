package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.navigation.*
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
class DrawerReturnTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var drawer: DrawerNavigation
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return AppViewModel(app).also { model -> compose.setContent { drawer = rememberDrawerNavigation(); KigttsApp(model, drawer) } }
    }
    private fun assertFullyClosed() {
        val sheet = compose.onNodeWithTag("app-drawer").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("Drawer must leave the screen completely: " + sheet, sheet.left.value >= root.right.value - 1f)
        compose.onNodeWithText("KIGTTS NEXT").assertIsNotDisplayed()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
    }
    @Test fun predictiveCommitClosesWholeDrawerAndCanReopen() {
        show()
        val dispatcher = compose.activity.onBackPressedDispatcher
        repeat(3) {
            compose.onNodeWithContentDescription("打开抽屉菜单").performClick()
            compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(1100f, 100f, 0f, BackEventCompat.EDGE_RIGHT)) }
            compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(900f, 100f, .75f, BackEventCompat.EDGE_RIGHT)) }
            compose.runOnIdle { dispatcher.onBackPressed() }
            assertFullyClosed()
        }
    }
    @Test fun repeatedBackDuringClosingDoesNotDismissUnderlyingSheet() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.onNodeWithContentDescription("打开抽屉菜单").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        val mid = compose.onNodeWithTag("app-drawer").getUnclippedBoundsInRoot()
        compose.runOnIdle { assertTrue(drawer.active); assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
        compose.onRoot().captureRoboImage("build/previews/drawer-close-mid.png")
        assertTrue(mid.left.value < compose.onRoot().getUnclippedBoundsInRoot().right.value)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        assertFullyClosed()
        compose.runOnIdle { assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
    }
    @Test fun backRequestedBeforeOpeningFrameDoesNotDismissUnderlyingSheet() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.waitForIdle()
        compose.runOnIdle { drawer.open(); compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFullyClosed()
        compose.runOnIdle { assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
    }
}
