package com.kigtts.next

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.kigtts.next.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class LandscapeRotationTest {
    @get:Rule(order = 0) val guide = CompletedGuideRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun rotationRebuildsTheHomeLayoutAndKeepsSeparateHeightAndColumnPreferences() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.runOnIdle { model.panelHeight(340f); model.panelColumns(3); model.openPanel(HomePanel.QUICK_TEXT) }
        compose.onNodeWithTag("landscape-app-rail").assertDoesNotExist()
        RuntimeEnvironment.setQualifiers("zh-rCN-w852dp-h393dp-land-xxhdpi")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("landscape-app-rail").assertIsDisplayed()
        assertSame(model, ViewModelProvider(compose.activity)[AppViewModel::class.java])
        assertEquals(340f, model.state.value.panelHeight); assertEquals(3, model.state.value.panelColumns)
        RuntimeEnvironment.setQualifiers("zh-rCN-w393dp-h852dp-port-xxhdpi")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("landscape-app-rail").assertDoesNotExist()
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
        assertEquals(340f, model.state.value.panelHeight); assertEquals(3, model.state.value.panelColumns)
    }
}
