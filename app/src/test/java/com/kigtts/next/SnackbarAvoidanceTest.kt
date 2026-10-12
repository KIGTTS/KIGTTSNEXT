package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.components.snackbarClearance
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class SnackbarAvoidanceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun toastTracksCollapsedToolbarThenExpandedTabsAndReleasesHomeAnchorOnSecondaryPage() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        listOf("quick-phrases","appearance","home-panel").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
        val model=testAppModel(app)
        compose.setContent { KigttsApp(model) }
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { model.message("提示") }
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        fun bottom()=compose.onNodeWithTag("app-snackbar").getUnclippedBoundsInRoot().bottom.value
        val toolbar=compose.onNodeWithTag("portrait-bottom-bar").getUnclippedBoundsInRoot().top.value
        assertTrue(bottom()<=toolbar)
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.mainClock.advanceTimeBy(700);compose.waitForIdle()
        val tabs=compose.onNodeWithTag("category-tab-bar").getUnclippedBoundsInRoot().top.value
        assertTrue(bottom()<=tabs);assertTrue("Avoid tabs, not the entire sheet",bottom()>compose.onNodeWithTag("portrait-bottom-bar").getUnclippedBoundsInRoot().top.value)
        compose.runOnIdle { model.navigate(Destination.Settings) }
        compose.mainClock.advanceTimeBy(700);compose.waitForIdle()
        assertTrue("Home anchor must be removed after entering a page",bottom()>tabs)
    }
    @Test fun clearanceRespectsSafeAreaKeyboardAndInvalidOffscreenAnchors() {
        assertEquals(80,snackbarClearance(1000,null,80,8))
        assertEquals(188,snackbarClearance(1000,820f,24,8))
        assertEquals(400,snackbarClearance(1000,820f,400,8))
        assertEquals(1000,snackbarClearance(1000,-300f,0,8))
    }
}
