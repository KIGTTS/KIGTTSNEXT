package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.state.Destination
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.navigation.*
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
class PageArrivalUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var settled = false
    private var starts = 0
    private var foreground by mutableStateOf(true)
    private lateinit var pull: CameraPullState

    private fun show(interactivePull: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val scope = rememberCoroutineScope()
            val state = remember { CameraPullState(scope, { "test" }, { foreground = false }, { "test" }).also {
                if (interactivePull) it.begin(CameraPullMode.ENTER, "home", null, 1000f, true)
            } }
            SideEffect { pull = state }
            val entry = remember { PageEntry(PageScene.Screen(Destination.CardCamera("test")), 1) }
            val pose = if (state.active) remember(state) { CameraPagePose(state, true) } else null
            PageMotion(entry, if (foreground) PageRole.FOREGROUND else PageRole.EXITING,
                foreground, 0f, 0, 0f, {}, cameraPose = pose) {
                val arrived = LocalPageSettled.current
                var started by remember { mutableStateOf(false) }
                SideEffect { settled = arrived }
                LaunchedEffect(arrived) { if (arrived && !started) { started = true; starts++ } }
                Box(Modifier.fillMaxSize())
            }
        } }
        compose.waitForIdle()
    }
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    @Test fun normalEntranceDefersHeavyContentUntilPageIsAtRest() {
        show(false); assertFalse(settled); assertEquals(0, starts)
        step(100); assertFalse(settled); assertEquals(0, starts)
        step(400); assertTrue(settled); assertEquals(1, starts)
    }
    @Test fun interactivePullDefersHeavyContentUntilReleasedAndFullyVisible() {
        show(true)
        compose.runOnIdle { pull.drag(400f) }; step(32)
        assertFalse(settled); assertEquals(0, starts)
        compose.runOnIdle { pull.finish(false) }; step(400)
        assertTrue(settled); assertEquals(1, starts)
    }
    @Test fun cancelledPullNeverStartsHeavyContent() {
        show(true)
        compose.runOnIdle { pull.drag(200f); pull.finish(true) }; step(500)
        assertFalse(settled); assertEquals(0, starts)
    }
}
