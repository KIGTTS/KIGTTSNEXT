package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
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
class CameraNativeControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun assertCircle(tag: String, minimum: Float) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width, bounds.height, 1f)
        assertTrue(bounds.width >= minimum - 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }

    @Test fun portraitCurrentAndPresetRatiosAreSingleLineCirclesWithFullTouchTargets() {
        var ratio by mutableFloatStateOf(3.3f); var minimum = 0f
        compose.setContent { KigttsTheme(ThemeSettings()) {
            minimum = with(LocalDensity.current) { 48.dp.toPx() }
            CameraZoomRail(ratio, 1f, 4f, true, { ratio = it }, false, Modifier.size(340.dp, 48.dp))
        } }
        for (tag in listOf("camera-zoom-current", "camera-zoom-stop/1", "camera-zoom-stop/2")) assertCircle(tag, minimum)
        compose.onNodeWithTag("camera-zoom-value", useUnmergedTree = true).assertTextEquals("3.3×")
        compose.onNodeWithTag("camera-zoom-current").performClick()
        assertEquals(1f, ratio, .001f)
    }

    @Test fun landscapeUsesANativeVerticalSliderAndPreservesTheSameCircleSizesAndRange() {
        var ratio by mutableFloatStateOf(3.3f); var minimum = 0f
        compose.setContent { KigttsTheme(ThemeSettings()) {
            minimum = with(LocalDensity.current) { 48.dp.toPx() }
            CameraZoomRail(ratio, 1.2f, 4f, true, { ratio = it }, true, Modifier.size(56.dp, 280.dp))
        } }
        assertCircle("camera-zoom-current", minimum); assertCircle("camera-zoom-stop/2", minimum)
        compose.onNodeWithTag("camera-zoom-stop/1").assertDoesNotExist()
        compose.onNodeWithTag("camera-zoom-current").performClick(); assertEquals(1.2f, ratio, .001f)
        compose.onNodeWithTag("camera-zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(cameraZoomPosition(3f, 1.2f..4f)) }
        assertEquals(3f, ratio, .001f)
    }
}
