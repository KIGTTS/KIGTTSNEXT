package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class PolaroidPinchAnchorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pinchInAScrolledGridKeepsTheTouchedPhotoAndReturnsToThatPhotoInStack() {
        val cards = (0 until 40).map { PolaroidCard(id = "anchor-$it", title = "照片 $it") }
        var mode by mutableStateOf(PolaroidBrowseMode.GRID)
        var columns by mutableIntStateOf(3)
        var focused by mutableStateOf(cards.first().id)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CardManagementTransition(false, browser = {
                PolaroidBrowser(cards, mode, focused, { null }, emptyList(), true,
                    { mode = it }, { focused = it.id }, {}, {}, Modifier.fillMaxSize(),
                    gridColumns = columns, changeColumns = { columns = it })
            }, manager = {})
        } }
        compose.onNodeWithTag("cards-grid").performScrollToIndex(12)
        compose.waitForIdle()
        val id = cards[19].id
        fun center() = compose.onNodeWithTag("polaroid/$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        fun pinchAtPhoto() {
            val browser = compose.onNodeWithTag("polaroid-browser")
            val position = center() - browser.fetchSemanticsNode().boundsInRoot.topLeft
            browser.performTouchInput {
                down(0, position - Offset(24f, 0f)); down(1, position + Offset(24f, 0f))
                moveTo(0, position - Offset(72f, 0f), 80); moveTo(1, position + Offset(72f, 0f), 80)
                up(0); up(1)
            }
            compose.waitForIdle()
        }
        val before = center()
        pinchAtPhoto()
        assertEquals(2, columns); assertEquals(id, focused)
        val after = center()
        assertTrue("The anchor row should remain near the gesture: $before -> $after", kotlin.math.abs(after.y - before.y) < 100f)
        pinchAtPhoto()
        assertEquals(PolaroidBrowseMode.STACK, mode); assertEquals(id, focused)
        compose.onNodeWithTag("cards-position").assertTextEquals("20 / 40")
    }
}
