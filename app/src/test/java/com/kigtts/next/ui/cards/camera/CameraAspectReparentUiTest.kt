package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CameraAspectReparentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun repeatedOverlayChangesKeepOnePreviewAndOneControlComposition() {
        var aspect by mutableFloatStateOf(.75f)
        var previewCreates = 0; var controlCreates = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(393.dp, 820.dp)) {
                CameraPageLayout(preview = { modifier, _ ->
                    remember { previewCreates++ }
                    Box(modifier.testTag("retained-preview"))
                }, controls = { vertical ->
                    remember { controlCreates++ }
                    CameraActionBar(false, true, true, true, {}, {}, {}, vertical, showCancel = false, gallery = {})
                }, photoAspect = aspect)
            }
        } }
        compose.mainClock.autoAdvance = false
        repeat(48) { index ->
            compose.runOnIdle { aspect = if (index % 2 == 0) .48f else .75f }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onNodeWithTag("retained-preview").assertIsDisplayed()
            compose.onNodeWithTag("camera-shutter").assertIsDisplayed()
        }
        assertEquals(1, previewCreates); assertEquals(1, controlCreates)
    }
}
