package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
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
@Config(sdk = [35], qualifiers = "zh-rCN-w900dp-h500dp-land-xhdpi")
class PolaroidPagerContinuityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun catalogRefreshCannotPullThePaperAwayFromAHeldFinger() {
        var cards by mutableStateOf((0..7).map { PolaroidCard(id = "photo-$it", title = "") })
        var focus by mutableStateOf("photo-0")
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(800.dp, 400.dp)) {
                PolaroidBrowser(cards, PolaroidBrowseMode.STACK, focus, { null }, emptyList(), true,
                    {}, { focus = it.id }, {}, {}, Modifier.fillMaxSize(), wideLayout = true)
            }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("cards-stack").performTouchInput {
            down(center); moveTo(center - Offset(width * .25f, 0f), 200)
        }
        step(32)
        val held = paperCenter("photo-0")
        compose.runOnIdle { cards = cards.map { it.copy(description = "刚更新的拍摄信息") } }
        step(160)
        assertEquals("A metadata update must not start an unsolicited positioning animation", held.x, paperCenter("photo-0").x, 1f)
        compose.onNodeWithTag("cards-stack").performTouchInput { moveBy(Offset(-80f, 0f), 40) }
        step(32)
        assertTrue("The same touch must still move the paper", paperCenter("photo-0").x < held.x - 4f)
        compose.onNodeWithTag("cards-stack").performTouchInput { up() }
        step(700)
    }

    @Test fun choosingADistantPhotoStartsFromTheCurrentlyPaintedPage() {
        val cards = (0..8).map { PolaroidCard(id = "photo-$it", title = "") }
        var focus by mutableStateOf("photo-0")
        var request by mutableLongStateOf(0L)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(800.dp, 400.dp)) {
                PolaroidBrowser(cards, PolaroidBrowseMode.STACK, focus, { null }, emptyList(), true,
                    {}, { focus = it.id }, {}, {}, Modifier.fillMaxSize(), wideLayout = true, positionRequest = request)
            }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        val before = paperCenter("photo-0")
        compose.runOnIdle { focus = "photo-7"; request++ }
        step(16)
        assertEquals("The first frame cannot teleport to the destination's neighbor", before.x, paperCenter("photo-0").x, 8f)
        step(700)
        compose.onNodeWithTag("cards-position").assertTextEquals("8 / 9")
    }

    private fun paperCenter(id: String): Offset {
        val node = compose.onNodeWithTag("polaroid/$id").fetchSemanticsNode().layoutInfo.coordinates
        return node.localToRoot(Offset(node.size.width / 2f, node.size.height / 2f))
    }
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
}
