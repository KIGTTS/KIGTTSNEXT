package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.ProvideButtonFeedback
import com.kigtts.next.ui.identity.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ticks = mutableListOf<HapticFeedbackType>()
    private val native = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType }
    }
    private fun content(enabled: () -> Boolean = { true }, body: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) {
            KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(enabled()) { Box(Modifier.width(360.dp)) { body() } } }
        } }
    }

    @Test fun tileClickIsSingleAndRespondsToTheGlobalSwitch() {
        var enabled by mutableStateOf(true); var opens = 0
        val tile = ProfileTile(id = "link", kind = ProfileTileKind.LINK, title = "网址", text = "https://example.com", columns = 1)
        content({ enabled }) { ProfileTileBoard(listOf(tile), emptyList(), { null }, openLink = { opens++ }) }
        compose.onNodeWithContentDescription("网址").performClick()
        assertEquals(listOf(HapticFeedbackType.VirtualKey), ticks); assertEquals(1, opens)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithContentDescription("网址").performClick()
        assertEquals(1, ticks.size); assertEquals(2, opens)
    }

    @Test fun tagDialogUsesTheHostSwitchAcrossTheDialogWindow() {
        var enabled by mutableStateOf(true)
        content({ enabled }) { ProfileTagsEditor(listOf("摄影"), {}, {}) }
        compose.onNodeWithText("摄影").performClick()
        assertEquals(listOf(HapticFeedbackType.VirtualKey), ticks)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("profile-tag-input").performTextInput("音乐")
        compose.onNodeWithText("添加标签").performClick()
        compose.onNodeWithText("保存标签").performClick()
        assertEquals(1, ticks.size)
    }

    @Test fun resizingTicksOnlyAcrossGridBoundariesAndDoesNotBuzzDuringSettlement() {
        var tiles by mutableStateOf(listOf(ProfileTile(id = "a", columns = 2)))
        content { ProfileTileBoard(tiles, emptyList(), { null }, editing = true, change = { tiles = it }) }
        val handle = compose.onNodeWithTag("tile-resize/a")
        handle.performTouchInput { down(center); moveBy(Offset(40f, 0f), 100); moveBy(Offset(10f, 0f), 40) }
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), ticks)
        handle.performTouchInput { moveBy(Offset(200f, 0f), 160) }
        assertEquals(HapticFeedbackType.SegmentFrequentTick, ticks.last())
        handle.performTouchInput { up() }
        assertEquals(HapticFeedbackType.GestureEnd, ticks.last())
        val count = ticks.size
        compose.mainClock.advanceTimeBy(500)
        assertEquals(count, ticks.size)
    }

    @Test fun cancelledResizeHasNoCompletionPulse() {
        content { ProfileTileBoard(listOf(ProfileTile(id = "a")), emptyList(), { null }, editing = true) }
        val handle = compose.onNodeWithTag("tile-resize/a")
        handle.performTouchInput { down(center); moveBy(Offset(80f, 0f), 100) }
        compose.runOnIdle { assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), ticks) }
        handle.performTouchInput { cancel() }
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), ticks)
    }
}
