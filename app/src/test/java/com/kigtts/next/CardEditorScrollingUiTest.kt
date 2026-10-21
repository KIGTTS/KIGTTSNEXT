package com.kigtts.next

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CardEditorScrollingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun scroll() = compose.onNodeWithTag("card-editor-tools-scroll")
    private fun position() = scroll().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun dragContent(upward: Boolean) {
        scroll().performTouchInput {
            // Start in the list viewport itself, rather than its header or a semantics scroll action.
            val upper = Offset(width * .92f, height * .18f)
            val lower = Offset(width * .92f, height * .82f)
            swipe(if (upward) lower else upper, if (upward) upper else lower, durationMillis = 600)
        }
        compose.waitForIdle()
    }

    @Test fun nativeTabsBelongToTheThreeDetentSheetAndLandscapeMovesItBesideThePaper() {
        var width by mutableStateOf(400.dp); var height by mutableStateOf(700.dp)
        var mode by mutableStateOf(CardEditorMode.CROP)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(width, height)) {
                CardEditorPanelScaffold(1f, true, false, {},
                    preview = { Text("照片", it.height(340.dp).testTag("detent-paper")) },
                    tools = { repeat(24) { Text("工具 $it", Modifier.fillMaxWidth().height(60.dp)) } },
                    categories = { compact, open -> CardEditorTabs(mode, { mode = it; open() }, compact) })
            }
        } }
        fun panel() = compose.onNodeWithTag("card-editor-panel")
        fun state(value: String) = panel().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
        fun tabs() = compose.onNodeWithTag("card-editor-tabs")
        state("PARTIAL")
        tabs().assert(hasAnyAncestor(hasTestTag("card-editor-panel")))
        assertEquals(panel().getUnclippedBoundsInRoot().bottom.value, tabs().getUnclippedBoundsInRoot().bottom.value, 1f)
        val partial = panel().getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        state("COLLAPSED"); tabs().assertIsDisplayed()
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        state("PARTIAL"); assertEquals(CardEditorMode.TEXT, mode)
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
            down(center); moveBy(Offset(0f, -180f * density), delayMillis = 600); advanceEventTime(300); up()
        }
        state("EXPANDED"); assertTrue(panel().getUnclippedBoundsInRoot().top < partial)
        tabs().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/editor-connected-portrait.png")
        compose.runOnIdle { width = 850.dp; height = 400.dp }
        compose.waitForIdle(); state("EXPANDED")
        val paper = compose.onNodeWithTag("detent-paper").fetchSemanticsNode().boundsInRoot
        assertTrue("The overlay sheet covers part of the stable landscape paper", paper.right > panel().fetchSemanticsNode().boundsInRoot.left)
        tabs().assertIsDisplayed().assert(hasAnyAncestor(hasTestTag("card-editor-panel")))
        compose.onRoot().captureRoboImage("build/previews/editor-connected-landscape.png")
        compose.onNodeWithTag("toggle-card-editor-panel").performClick(); state("COLLAPSED")
        compose.onNodeWithTag("toggle-card-editor-panel").performClick(); state("PARTIAL")
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
            down(center); moveBy(Offset(-160f * density, 0f), delayMillis = 600); advanceEventTime(300); up()
        }
        state("EXPANDED"); assertEquals(CardEditorMode.TEXT, mode)
        dragContent(true); assertTrue(position() > 60f)
    }

    @Test fun contentDragsMoveRealRowsInBothDirectionsWithoutDraggingOrCollapsingThePanel() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(400.dp, 700.dp)) {
                CardEditorPanelScaffold(1f, true, false, {}, preview = { Text("照片", it) }, tools = {
                    repeat(24) { index -> Text("工具 $index", Modifier.fillMaxWidth().height(60.dp).testTag("scroll-tool/$index")) }
                })
            }
        } }
        val panelTop = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top
        val firstTop = compose.onNodeWithTag("scroll-tool/0").fetchSemanticsNode().boundsInRoot.top
        assertEquals(0f, position(), .1f)
        dragContent(true)
        assertTrue("A real middle-of-list swipe must scroll tools", position() > 60f)
        val movedTop = compose.onNodeWithTag("scroll-tool/0").fetchSemanticsNode().boundsInRoot.top
        assertTrue(movedTop < firstTop - 60f)
        assertEquals(panelTop, compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top, .5f)
        repeat(3) { dragContent(true) }
        val lower = position()
        dragContent(false)
        assertTrue("The reverse gesture must scroll back, not fold the sheet", position() < lower)
        repeat(8) { dragContent(false) }
        assertEquals(0f, position(), .1f)
        assertEquals(panelTop, compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top, .5f)
        compose.onNodeWithTag("scroll-tool/0").assertIsDisplayed()
    }

    @Test fun onlyTheHeaderCanFoldAndReopenTheSheetAndThePaperStaysStillBehindIt() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(400.dp, 700.dp)) {
                CardEditorPanelScaffold(1f, true, false, {}, preview = { Text("照片", it.testTag("scroll-test-paper")) },
                    tools = { repeat(24) { Text("工具 $it", Modifier.height(60.dp)) } })
            }
        } }
        val expanded = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top
        val originalPaper = compose.onNodeWithTag("scroll-test-paper").fetchSemanticsNode().boundsInRoot
        // The connected sheet now resizes its visible viewport during a drag. Read the
        // detent travel before moving it, not from the already shortened viewport.
        val travel = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.height -
            compose.onNodeWithTag("card-editor-panel-header").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
            down(center); moveTo(center + Offset(0f, 160f), delayMillis = 250)
        }
        compose.waitForIdle()
        val during = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The header drag must move the actual native sheet before release", during > expanded + 100f)
        val paperDuring = compose.onNodeWithTag("scroll-test-paper").fetchSemanticsNode().boundsInRoot
        assertEquals(originalPaper, paperDuring)
        // Keep the live motion assertion and then cross the final detent midpoint.
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
            moveBy(Offset(0f, travel * .9f - 160f), delayMillis = 250); advanceEventTime(300); up()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("展开编辑面板").assertIsDisplayed()
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
            down(center); moveBy(Offset(0f, -travel * .9f), delayMillis = 600); advanceEventTime(300); up()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("收起编辑面板").assertIsDisplayed()
        assertEquals(expanded, compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top, .5f)
        assertEquals(0f, position(), .1f)
    }

    @Test fun realEditorToolsStayScrollableAfterTabChangesKeyboardInsetsAndPortraitLandscapeResizing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root = File(external, "appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(external.toPath()) && root != external); root.deleteRecursively()
        app.getSharedPreferences("contact-cards", 0).edit().putString("items", "[]").commit()
        val paths = PluginPaths(app); val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val cards = CardRepository(app, stickers)
        val card = cards.create(PolaroidCard(title = ""))
        // Compact home-style controls fit without scrolling on tall screens. Start in a shorter
        // real window to exercise overflow, then retain the keyboard and rotation checks below.
        var width by mutableStateOf(400.dp); var height by mutableStateOf(620.dp)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(width, height)) { CardPageScaffold("编辑拍立得", {}, subtitle = null) {
                CardEditorScreen(cards, stickers, card.id, {})
            } }
        } }
        dragContent(true)
        assertTrue(position() > 20f)
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        compose.onNodeWithTag("card-title").performScrollTo().performTextReplacement("保留的草稿")
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            val density = compose.activity.resources.displayMetrics.density
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (220 * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build()
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        compose.waitForIdle()
        val keyboardViewport = scroll().fetchSemanticsNode().boundsInRoot
        assertTrue("Keyboard layout retains a real nonzero scrolling viewport", keyboardViewport.height > 32f)
        dragContent(true)
        assertTrue(position() > 20f)
        repeat(8) { dragContent(false) }
        assertEquals(0f, position(), .1f)
        compose.onNodeWithTag("card-editor-tab/STICKERS").performClick()
        compose.onNodeWithText("添加文字", substring = false).performClick()
        compose.onNodeWithTag("card-layer-text").performScrollTo().performTextReplacement("尚未保存的贴纸文字")
        compose.runOnIdle { width = 850.dp; height = 400.dp }
        compose.waitForIdle()
        val panelTop = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top
        val before = position()
        dragContent(false)
        assertTrue("A tiny wide/IME tools viewport must still scroll backward", position() < before)
        assertEquals(panelTop, compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot.top, .5f)
        compose.onNodeWithTag("card-layer-text").assertTextContains("尚未保存的贴纸文字")
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        compose.onNodeWithTag("card-title").performScrollTo().assertTextContains("保留的草稿")
        assertEquals(card, cards.cards.value.single())
    }
}
