package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.components.DragFeedback
import com.kigtts.next.ui.components.reorder.*
import com.kigtts.next.ui.home.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class QuickTextHoldMotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun aStationaryHoldVibratesBeforeMovementAndTheMenuStaysForItsExitAnimation() {
        val pulses = mutableListOf<HapticFeedbackType>()
        val feedback = DragFeedback(object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
        })
        val item = ReorderTarget("phrase", Rect(0f, 0f, 180f, 80f))
        val drag = ReorderDrag({ listOf(item) }, { _, _ -> }, feedback = feedback)
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        var menu by mutableStateOf<QuickTextMenuTarget?>(null)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { MaterialTheme {
            Box(Modifier.size(200.dp).testTag("hold-pane").reorderGestures(drag,
                onStationaryHold = { menu = it.menuTarget() }))
            QuickTextContextMenu(menu, model, { menu = null }, {})
        } } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("hold-pane").performTouchInput { down(Offset(90f, 40f)); advanceEventTime(700); moveBy(Offset.Zero) }
        compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), pulses)
        assertNull("The stationary hold has not lifted the card", drag.key)
        compose.onNodeWithTag("hold-pane").performTouchInput { up() }
        repeat(16) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithTag("quick-text-context-menu").assertExists()
        compose.runOnIdle { menu = null }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.onNodeWithTag("quick-text-context-menu").assertExists()
        repeat(14) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithTag("quick-text-context-menu").assertDoesNotExist()
    }

    @Test fun droppingRevealsTheListBeforeRemovingTheFadingForeground() {
        val item = ReorderTarget("phrase", Rect(0f, 0f, 180f, 80f))
        val drag = ReorderDrag({ listOf(item) }, { _, _ -> }, animateDrop = true)
        drag.begin(item, Offset(90f, 40f)); drag.drag(Offset(0f, 60f))
        compose.setContent { rememberReorderDrop(drag, fadeOverlay = true) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { drag.finish() }
        var sawFade = false
        repeat(40) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            if (drag.overlayAlpha > 0f && drag.overlayAlpha < 1f) {
                assertTrue(drag.landed); assertEquals("phrase", drag.key); sawFade = true
            }
        }
        assertTrue("The foreground must fade after landing (alpha=${drag.overlayAlpha})", sawFade)
        repeat(14) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertNull(drag.key)
    }
}
