package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
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

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CardEditorElementDragUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val inserted = mutableListOf<CardLayer>()
    private var clicked = 0
    private fun screen(landscape: Boolean) {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(if (landscape) 850.dp else 400.dp, if (landscape) 400.dp else 700.dp)) {
                CardEditorPanelScaffold(1f, true, false, {},
                    preview = { Box(it.aspectRatio(1f).testTag("drop-paper")) },
                    tools = { StickerLibraryPanel(listOf(StickerLibrary("lib", "贴纸")),
                        listOf(StickerItem(id = "test", libraryId = "lib", imageId = "test-image", name = "示例")),
                        "lib", {}, { null }, true, {}, {}, { clicked++ }, {}, Modifier.height(260.dp), false) },
                    insertElement = { inserted += it })
            }
        } }
    }
    private fun drag(landscape: Boolean) {
        screen(landscape)
        compose.onNodeWithTag("sticker-item/test").performClick(); assertEquals(1, clicked)
        val paper = compose.onNodeWithTag("drop-paper").fetchSemanticsNode().boundsInRoot
        val item = compose.onNodeWithTag("sticker-item/test").fetchSemanticsNode().boundsInRoot
        val target = Offset(paper.center.x, paper.top + paper.height * .25f)
        compose.onRoot().performTouchInput {
            down(item.center); advanceEventTime(650); moveTo(target, delayMillis = 300)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("card-element-drag-preview").assertIsDisplayed()
        compose.onNodeWithTag("card-editor-panel").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "COLLAPSED"))
        assertEquals("The tools overlay the paper without moving its drop coordinates", paper,
            compose.onNodeWithTag("drop-paper").fetchSemanticsNode().boundsInRoot)
        compose.onRoot().performTouchInput { moveTo(target + Offset(8f, 0f)); up() }
        compose.waitForIdle()
        assertEquals(1, inserted.size); assertEquals(1, clicked)
        assertEquals("test-image", inserted.single().imageId)
        assertEquals(.25, inserted.single().y, .01)
        compose.onNodeWithTag("card-element-drag-preview").assertDoesNotExist()
    }
    @Test fun portraitStickerDragSurvivesAutomaticSheetCollapseAndDropsAtThePaperPosition() = drag(false)
    @Test fun landscapeStickerDragSurvivesAutomaticSideSheetCollapse() = drag(true)
    @Test fun releasingOutsideThePaperDoesNotAddAnything() {
        screen(false)
        val item = compose.onNodeWithTag("sticker-item/test").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(item.center); advanceEventTime(650); moveTo(Offset(4f, 4f), delayMillis = 300); up() }
        compose.waitForIdle()
        assertTrue(inserted.isEmpty()); assertEquals(0, clicked)
        compose.onNodeWithTag("card-element-drag-preview").assertDoesNotExist()
    }
}
