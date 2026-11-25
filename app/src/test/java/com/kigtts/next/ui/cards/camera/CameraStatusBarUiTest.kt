package com.kigtts.next.ui.cards.camera

import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [29])
class CameraStatusBarUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cameraHidesStatusAfterFocusReturnsAndRestoresWindowOnExit() {
        var active by mutableStateOf(false)
        compose.setContent { com.kigtts.next.ui.components.HideStatusBar(active) }
        val window = compose.activity.window
        val originalFlags = window.attributes.flags
        compose.runOnIdle { active = true }
        compose.waitForIdle()
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0)
        compose.runOnIdle {
            window.decorView.systemUiVisibility = 0
            window.decorView.viewTreeObserver.let { observer ->
                observer.javaClass.getDeclaredMethod("dispatchOnWindowFocusChange", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(observer, true)
            }
        }
        compose.waitForIdle()
        assertTrue(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertEquals(0, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        compose.runOnIdle { active = false }
        compose.waitForIdle()
        assertEquals(originalFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN,
            window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
        assertEquals(0, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN)
        // A queued focus callback from the camera must not hide bars after it has left.
        compose.runOnIdle { window.decorView.viewTreeObserver.let { observer ->
            observer.javaClass.getDeclaredMethod("dispatchOnWindowFocusChange", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(observer, true)
        } }
        compose.waitForIdle()
        assertEquals(0, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
}
