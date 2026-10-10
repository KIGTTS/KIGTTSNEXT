package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.LocalCornerRadius
import com.kigtts.next.ui.components.ProvideButtonFeedback
import com.kigtts.next.ui.settings.SettingChoiceGroup
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SettingsImmediateFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    @Test fun downTicksImmediatelyAndAQuickTapStillDeformsThenRestoresWithoutASecondTick() {
        val settings = mutableStateOf(ThemeSettings()); val feedback = mutableListOf<HapticFeedbackType>()
        val native = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType } }
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { KigttsTheme(settings.value) {
            ProvideButtonFeedback(true) { SettingChoiceGroup("对比度", ContrastLevel.entries, settings.value.contrast, { it.label },
                { settings.value = settings.value.copy(contrast = it) }, Modifier.width(320.dp)) }
        } } }
        fun width(label: String) = compose.onNodeWithContentDescription("对比度：$label").getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        val rest = width("中"); val button = compose.onNodeWithContentDescription("对比度：中")
        button.performTouchInput { down(center) }; assertEquals(listOf(HapticFeedbackType.VirtualKey), feedback)
        button.performTouchInput { up() }; step(48)
        assertTrue("Short tap must visibly expand", width("中") > rest + .5f)
        assertEquals(ContrastLevel.MEDIUM, settings.value.contrast); assertEquals(1, feedback.size)
        repeat(12) { i ->
            val next = compose.onNodeWithContentDescription("对比度：" + ContrastLevel.entries[i % 3].label)
            next.performTouchInput { down(center); advanceEventTime(8); up() }; step(16)
        }
        step(1200)
        val widths = ContrastLevel.entries.map { width(it.label) }
        assertTrue("Rapid taps return all widths: $widths", widths.max() - widths.min() < .5f)
        assertEquals(13, feedback.size)
    }
    @Test fun switchingCornerLevelsPassesThroughIntermediateRadiiAndSettlesAtTheLatestChoice() {
        val settings = mutableStateOf(ThemeSettings()); var radius = 0f
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(settings.value) { val current = LocalCornerRadius.current; SideEffect { radius = current.value } } }
        compose.waitForIdle(); assertEquals(24f, radius, .01f)
        compose.runOnIdle { settings.value = settings.value.copy(cornerLevel = CornerLevel.SMALL) }; step(16); step(80)
        assertTrue("Animated radius: $radius", radius > CornerLevel.SMALL.radius && radius < 24f)
        compose.runOnIdle { settings.value = settings.value.copy(cornerLevel = CornerLevel.MEDIUM) }; step(16); step(500)
        assertEquals(CornerLevel.MEDIUM.radius, radius, .01f)
    }
}
