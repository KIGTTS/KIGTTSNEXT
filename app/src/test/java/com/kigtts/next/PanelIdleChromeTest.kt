package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PanelIdleChromeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun idleStateDoesNotRemoveControlsAndFirstTouchStillActivates() {
        lateinit var state: PanelIdleChrome
        var clicks = 0
        compose.setContent {
            val (idle, alpha) = rememberPanelIdleChrome(true)
            state = idle
            Box(Modifier.size(200.dp).panelIdleWake(idle)) {
                Text("操作", Modifier.testTag("control").clickable { clicks++ })
            }
        }
        val before = compose.onNodeWithTag("control").getUnclippedBoundsInRoot()
        compose.runOnIdle { state.idle = true }
        compose.onNodeWithTag("control").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); assertFalse(state.idle) }
        assertEquals(before, compose.onNodeWithTag("control").getUnclippedBoundsInRoot())
    }
}
