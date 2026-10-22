package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.media.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35])
class SubtitleRendererWindowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun brightnessAndAwakeOnlyApplyWhileRendererVisibleAndRestorePreviousWindow() {
        val active = mutableStateOf(true)
        val settings = mutableStateOf(RendererWindowSettings(true, false, .6f, true))
        var view: android.view.View? = null
        compose.activity.window.attributes = compose.activity.window.attributes.apply { screenBrightness = .3f }
        compose.setContent {
            val local = LocalView.current
            SideEffect { view = local }
            SubtitleRendererWindow(settings.value, active.value)
        }
        compose.runOnIdle { assertTrue(view!!.keepScreenOn); assertEquals(.6f, compose.activity.window.attributes.screenBrightness, .001f)
            settings.value = settings.value.copy(followSystemBrightness = true) }
        compose.runOnIdle { assertEquals(-1f, compose.activity.window.attributes.screenBrightness, .001f); active.value = false }
        compose.runOnIdle { assertFalse(view!!.keepScreenOn); assertEquals(.3f, compose.activity.window.attributes.screenBrightness, .001f) }
        val parsed = RendererWindowSettings.parse("""{"screenBrightness":99,"quickSwipeOpensQuickText":true}""")
        assertEquals(1f, parsed.brightness); assertTrue(parsed.quickSwipe)
    }
}
