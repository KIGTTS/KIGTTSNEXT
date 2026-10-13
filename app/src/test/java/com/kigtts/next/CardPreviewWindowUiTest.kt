package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardPreviewDialog
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CardPreviewWindowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private data class Actions(var shares: Int = 0, var edits: Int = 0, var dismisses: Int = 0)
    private fun show(format: PolaroidFormat = PolaroidFormat.MINI): Pair<PolaroidCard, Actions> {
        val card = PolaroidCard(title = "透明预览", frameStyleId = "test")
        val frame = PolaroidFrameStyle("test", "测试", format = format)
        val actions = Actions(); var visible by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.fillMaxSize().background(Color(0xFF408060)).testTag("preview-host"))
            if (visible) CardPreviewDialog(card, { null }, frame,
                { actions.dismisses++; visible = false }, { actions.edits++; visible = false },
                { actions.shares++ }, {}, {}, {})
        } }
        return card to actions
    }

    @Test fun transparentPreviewFillsTheWindowKeepsPaperAspectAndPlacesShareAndEditAtTheBottom() {
        val (card, actions) = show()
        val host = compose.onNodeWithTag("preview-host").fetchSemanticsNode().boundsInRoot
        val window = compose.onNodeWithTag("card-preview-window").fetchSemanticsNode().boundsInRoot
        val paper = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("card-preview-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(window.width >= host.width - 1f && window.height >= host.height - 1f)
        assertEquals(PolaroidFormat.MINI.geometry.aspectRatio, (paper.width / paper.height).toDouble(), .01)
        assertTrue(paper.bottom <= controls.top + 1f && controls.bottom > window.bottom - 200f)
        compose.onNodeWithTag("alert-dialog-container").assertDoesNotExist()
        val share = compose.onNodeWithTag("share-card-preview").fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithTag("edit-card-preview").fetchSemanticsNode().boundsInRoot
        assertTrue(share.center.x < edit.center.x)
        compose.onNodeWithTag("share-card-preview").performClick()
        assertEquals(1, actions.shares)
        compose.onNodeWithTag("card-preview-window").assertExists()
        compose.onNodeWithTag("card-preview-window").captureRoboImage("build/previews/polaroid-transparent-preview-portrait.png")
    }

    @Test fun editingWaitsForThePreviewExitAndRunsExactlyOnce() {
        val (_, actions) = show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("edit-card-preview").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("card-preview-window").assertExists()
        assertEquals(0, actions.edits)
        compose.onNodeWithTag("edit-card-preview").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        assertEquals(1, actions.edits)
        compose.onNodeWithTag("card-preview-window").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun widePaperFitsTheLandscapeWindowAndTappingEmptySpaceDismissesIt() {
        val (card, actions) = show(PolaroidFormat.WIDE)
        val window = compose.onNodeWithTag("card-preview-window").fetchSemanticsNode().boundsInRoot
        val paper = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("card-preview-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(PolaroidFormat.WIDE.geometry.aspectRatio, (paper.width / paper.height).toDouble(), .01)
        assertTrue(paper.top >= window.top && paper.bottom <= controls.top + 1f)
        compose.onNodeWithTag("card-preview-window").captureRoboImage("build/previews/polaroid-transparent-preview-landscape.png")
        compose.onNodeWithTag("card-preview-window").performTouchInput { click(Offset(10f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(1, actions.dismisses)
        compose.onNodeWithTag("card-preview-window").assertDoesNotExist()
    }
}
