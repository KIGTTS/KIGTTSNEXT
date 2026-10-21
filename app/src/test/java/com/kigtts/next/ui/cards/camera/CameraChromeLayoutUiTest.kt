package com.kigtts.next.ui.cards.camera

import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.excludeCameraPull
import com.kigtts.plugin.api.PolaroidFormat
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
class CameraChromeLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var nativeView: PreviewView? = null
    private var ratio = 1f
    private var clicks = 0

    @Composable private fun Preview(format: PolaroidFormat, width: Int, height: Int, vertical: Boolean) {
        val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
        val view = remember { PreviewView(context).apply { id = View.generateViewId() }.also { nativeView = it } }
        val session = remember { PolaroidCameraSession(context, owner, scope, {}) }
        CameraPaperPreview(view, format, PhotoFilter.ORIGINAL, session, {}, Modifier.size(width.dp, height.dp),
            displayReady = true, verticalChrome = vertical, chrome = { layout ->
                CameraOverlayControls(format, listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE), PhotoFilter.ORIGINAL,
                    true, vertical, false, {}, { clicks++ }, {}, {}, layout = layout)
                CameraZoomRail(ratio, 1f, 4f, true, { ratio = it }, vertical,
                    Modifier.align(Alignment.TopStart).cameraChromeBounds(layout.zoom).excludeCameraPull())
            })
    }

    @Test fun roomyPortraitPlacesChromeOutsideAndBothExternalControlsRemainInteractive() {
        compose.setContent { KigttsTheme(ThemeSettings()) { Preview(PolaroidFormat.SQUARE, 360, 620, false) } }
        val photo = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        val strip = compose.onNodeWithTag("camera-switch-strip").fetchSemanticsNode().boundsInRoot
        val zoom = compose.onNodeWithTag("camera-zoom-rail").fetchSemanticsNode().boundsInRoot
        assertTrue(strip.bottom < photo.top); assertTrue(zoom.top > photo.bottom)
        compose.onNodeWithTag("camera-settings").performClick(); assertEquals(1, clicks)
        compose.onNodeWithTag("camera-zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(cameraZoomPosition(2f, 1f..4f)) }
        assertEquals(2f, ratio, .001f)
        assertSame(nativeView, compose.activity.findViewById<View>(nativeView!!.id))
    }

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun roomyLandscapePlacesTheSwitchesOnTheLeftAndZoomOnTheRight() {
        compose.setContent { KigttsTheme(ThemeSettings()) { Preview(PolaroidFormat.SQUARE, 700, 300, true) } }
        val photo = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        val strip = compose.onNodeWithTag("camera-switch-strip").fetchSemanticsNode().boundsInRoot
        val zoom = compose.onNodeWithTag("camera-zoom-rail").fetchSemanticsNode().boundsInRoot
        assertTrue(strip.right < photo.left); assertTrue(zoom.left > photo.right)
        compose.onNodeWithTag("camera-settings").performClick(); assertEquals(1, clicks)
        assertSame(nativeView, compose.activity.findViewById<View>(nativeView!!.id))
    }

    @Test fun changingRatioKeepsTheNativeViewAndMovesChromeContinuouslyBackInside() {
        var format by mutableStateOf(PolaroidFormat.SQUARE)
        compose.setContent { KigttsTheme(ThemeSettings()) { Preview(format, 360, 500, false) } }
        val retained = nativeView
        val initial = compose.onNodeWithTag("camera-switch-strip").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { format = PolaroidFormat.MINI }
        repeat(8) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val intermediate = compose.onNodeWithTag("camera-switch-strip").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(initial.top - intermediate.top) > 1f)
        repeat(25) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val photo = compose.onNodeWithTag("camera-photo-window").fetchSemanticsNode().boundsInRoot
        val final = compose.onNodeWithTag("camera-switch-strip").fetchSemanticsNode().boundsInRoot
        assertTrue(final.top >= photo.top - 1f && final.bottom <= photo.bottom + 1f)
        assertSame(retained, nativeView); assertSame(retained, compose.activity.findViewById<View>(retained!!.id))
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("camera-settings").performClick(); assertEquals(1, clicks)
    }
}
