package com.kigtts.next.overlay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.settings.OverlaySettingsList
import com.kigtts.next.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class OverlaySettingsReorderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun swipeLongPressAndMoreShareOneMenuWithoutStartingReorder() {
        var entries by mutableStateOf(defaultOverlayShortcuts())
        var edits = 0; var writes = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface {
            OverlaySettingsList(entries, { writes++ }, { edits++ }, {}, {},
                remove = { entry -> entries = entries.filterNot { it.id == entry.id } })
        } } }
        compose.onNodeWithTag("reveal-action/overlay/camera/delete").assertDoesNotExist()
        compose.onNodeWithTag("reveal-row/overlay/camera").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("reveal-action/overlay/camera/settings").assertIsDisplayed().performClick()
        assertEquals(1, edits); assertEquals(0, writes)
        compose.onNodeWithText("相册").performTouchInput { longClick() }
        compose.onNodeWithTag("overlay-floating-entry", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("reveal-action/overlay/cards/delete").assertIsDisplayed()
        compose.onNodeWithTag("overlay-more/sketch").performClick()
        compose.onNodeWithTag("reveal-action/overlay/cards/delete").assertDoesNotExist()
        val row = compose.onNodeWithTag("overlay-entry:sketch").getUnclippedBoundsInRoot()
        val action = compose.onNodeWithTag("reveal-action/overlay/sketch/delete").getUnclippedBoundsInRoot()
        assertEquals((row.bottom - row.top).value, (action.bottom - action.top).value, .5f)
        compose.onNodeWithTag("reveal-action/overlay/sketch/delete").performClick()
        compose.onNodeWithTag("overlay-entry:sketch").assertDoesNotExist()
        assertEquals(0, writes)
    }
    @Test fun settingsHandleFollowsTheFingerAcrossRowsAndPersistsOnlyOnRelease() {
        var entries by mutableStateOf(defaultOverlayShortcuts())
        var writes = 0; var edits = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface {
            OverlaySettingsList(entries, { ids -> writes++; entries = ids.map(entries.associateBy { it.id }::getValue) },
                { edits++ }, {}, { Text("悬浮窗设置") })
        } } }
        val before = compose.onNodeWithTag("overlay-entry:camera").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("overlay-shortcuts-list").fetchSemanticsNode().boundsInRoot
        val grip = compose.onNodeWithContentDescription("拖动排序拍照", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - viewport.topLeft
        val delta = Offset(0f, before.height * 1.45f)
        compose.onNodeWithTag("overlay-shortcuts-list").performTouchInput { down(grip); moveBy(delta, 200) }
        compose.waitForIdle()
        val held = compose.onNodeWithTag("overlay-floating-entry", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, held.left, 1f); assertEquals(before.top + delta.y, held.top, 2f)
        assertEquals(0, writes); assertEquals(0, edits)
        compose.onNodeWithTag("overlay-shortcuts-list").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, writes); assertEquals(0, edits)
        assertTrue(entries.indexOfFirst { it.id == "camera" } > 1)
        compose.onNodeWithTag("overlay-floating-entry", useUnmergedTree = true).assertDoesNotExist()
    }
}
