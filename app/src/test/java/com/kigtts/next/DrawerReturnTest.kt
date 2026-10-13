package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** Regression coverage for the direct settings entrance that replaces the former drawer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class DrawerReturnTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) } }
    }
    private fun assertHome(model: AppViewModel) {
        compose.onNodeWithTag("app-drawer").assertDoesNotExist()
        compose.onNodeWithTag("speech-editor").assertIsDisplayed()
        compose.onNodeWithContentDescription("打开设置").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
    }
    @Test fun predictiveCommitReturnsTheWholeSettingsPageAndCanReopen() {
        val model = show(); val dispatcher = compose.activity.onBackPressedDispatcher
        repeat(3) {
            compose.onNodeWithContentDescription("打开设置").performClick()
            compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination) }
            compose.onNodeWithTag("app-drawer").assertDoesNotExist()
            compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(1100f, 100f, 0f, BackEventCompat.EDGE_RIGHT)) }
            compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(900f, 100f, .75f, BackEventCompat.EDGE_RIGHT)) }
            compose.runOnIdle { dispatcher.onBackPressed() }
            assertHome(model)
        }
    }
    @Test fun settingsReturnDirectionIsFixedRegardlessOfGestureEdge() {
        val model = show(); compose.onNodeWithContentDescription("打开设置").performClick()
        val page = compose.onNodeWithTag("navigation-secondary")
        val original = page.getUnclippedBoundsInRoot(); val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        val previews = listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT).map { edge ->
            compose.runOnIdle {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .7f, edge))
            }
            compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
            val preview = page.getUnclippedBoundsInRoot()
            assertTrue(preview.left.value > original.left.value)
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
            compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination) }
            preview
        }
        assertEquals(previews[0], previews[1]); compose.mainClock.autoAdvance = true
    }
    @Test fun repeatedBackDuringPageExitDoesNotDismissUnderlyingQuickPanel() {
        val model = show(); compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.onNodeWithContentDescription("打开设置").performClick(); compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        assertHome(model)
        compose.runOnIdle { assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
    }
}
