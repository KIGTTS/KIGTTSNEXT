package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.AnchoredDraggableState
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
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w900dp-h900dp-xhdpi")
class PolaroidAlbumHandleUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun portraitHandlePicksUpBeforeMovingAndSnapsWithoutStealingPhotoScroll() = exercise(false)
    @Test fun landscapeIsTheCounterclockwisePortraitLayoutAndKeepsItsDragOwner() = exercise(true)

    private fun exercise(wide: Boolean) {
        lateinit var sheet: AnchoredDraggableState<Int>
        val pulses = mutableListOf<HapticFeedbackType>()
        val haptic = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType } }
        val photos = (0..14).map { PolaroidCard(id = "photo-$it", title = "") }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                sheet = rememberCardAlbumSheet()
                Box(Modifier.size(if (wide) 850.dp else 400.dp, if (wide) 400.dp else 780.dp)) {
                    CardAlbumDock(listOf(PolaroidAlbum(id = "album", name = "相册")), "album", photos, "photo-0", { null },
                        true, wide, sheet.targetValue > 0, {}, {}, {}, {}, {}, {},
                        Modifier.align(if (wide) androidx.compose.ui.Alignment.CenterEnd else androidx.compose.ui.Alignment.BottomCenter), sheet = sheet)
                }
            }
        } }
        compose.onNodeWithTag("card-album-photos").assertDoesNotExist()
        val resting = compose.onNodeWithTag("card-album-grip", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onNodeWithTag("card-album-handle").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals("Pickup happens on down, before any drag threshold", listOf(HapticFeedbackType.GestureThresholdActivate), pulses)
        val pressed = compose.onNodeWithTag("card-album-grip", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(if (wide) pressed.right - pressed.left > resting.right - resting.left
            else pressed.bottom - pressed.top > resting.bottom - resting.top)
        fun moveToNext(distance: Float, release: Boolean = true) {
            compose.onNodeWithTag("card-album-handle").performTouchInput {
                moveBy(if (wide) Offset(-distance * density, 0f) else Offset(0f, -distance * density), 400)
                advanceEventTime(300)
                if (release) up()
            }
            compose.waitForIdle()
        }
        moveToNext(if (wide) 100f else 110f)
        assertEquals(1, sheet.currentValue)
        compose.onNodeWithTag("card-album-photos").assertIsDisplayed()
        compose.onNodeWithTag("card-albums").assertDoesNotExist()
        compose.onNodeWithTag("card-album-handle").performTouchInput { down(center) }
        moveToNext(if (wide) 130f else 66f)
        assertEquals(2, sheet.currentValue)
        compose.onNodeWithTag("card-albums").assertIsDisplayed()
        val toggle = compose.onNodeWithTag("toggle-card-albums").getUnclippedBoundsInRoot()
        val create = compose.onNodeWithTag("create-card-album").getUnclippedBoundsInRoot()
        val more = compose.onNodeWithTag("card-album-menu").getUnclippedBoundsInRoot()
        val grip = compose.onNodeWithTag("card-album-grip", useUnmergedTree = true).getUnclippedBoundsInRoot()
        if (wide) listOf(toggle, create, more).forEach { button ->
            assertEquals("All actions must share the vertical grip centerline",
                (grip.left + grip.right).value / 2, (button.left + button.right).value / 2, .5f)
        }
        if (wide) assertTrue(toggle.bottom < create.top && create.bottom < grip.top && grip.bottom < more.top)
        else assertTrue(more.right < grip.left && grip.right < create.left && create.right < toggle.left)
        compose.onNodeWithTag("card-album-photos").performTouchInput {
            if (wide) swipeUp() else swipeLeft()
        }
        assertEquals("Scrolling photos must not resize their panel", 2, sheet.currentValue)
        compose.onNodeWithTag("card-album-handle").performTouchInput { down(center) }
        moveToNext(-250f)
        assertEquals(0, sheet.currentValue)
        compose.onNodeWithTag("card-album-photos").assertDoesNotExist()
        assertTrue(pulses.contains(HapticFeedbackType.SegmentFrequentTick))
        assertTrue(pulses.contains(HapticFeedbackType.GestureEnd))
    }
}
