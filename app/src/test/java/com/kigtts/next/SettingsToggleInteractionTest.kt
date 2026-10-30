package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.settings.ToggleSetting
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SettingsToggleInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun labelAndSwitchShareOneToggleActionAndOneHaptic() {
        var checked by mutableStateOf(false)
        var calls = 0
        val pulses = mutableListOf<HapticFeedbackType>()
        val feedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
        }
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalHapticFeedback provides feedback) {
            ToggleSetting("测试开关", "说明", null, checked, { checked = it; calls++ })
        } } }
        val row = compose.onNodeWithContentDescription("测试开关")
        row.assertIsOff().performTouchInput { click(Offset(30f, center.y)) }
        row.assertIsOn()
        row.performTouchInput { click(Offset(width - 40f, center.y)) }
        row.assertIsOff()
        row.performClick().assertIsOn()
        compose.runOnIdle { assertEquals(3, calls); assertEquals(3, pulses.size) }
    }

    @Test fun disabledRowCannotToggleFromEitherSide() {
        var calls = 0
        compose.setContent { MaterialTheme { ToggleSetting("锁定开关", "", null, false, { calls++ }, enabled = false) } }
        val row = compose.onNodeWithContentDescription("锁定开关").assertIsNotEnabled()
        row.performTouchInput { click(Offset(30f, center.y)) }
        row.performTouchInput { click(Offset(width - 40f, center.y)) }
        row.assertIsOff()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
