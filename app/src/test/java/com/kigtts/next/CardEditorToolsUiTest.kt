package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.cards.CardEditorScreen
import com.kigtts.next.ui.navigation.PageScaffold
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CardEditorToolsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var backs = 0
    private val messages = mutableListOf<String>()

    private fun show(card: PolaroidCard): Pair<CardRepository, StickerRepository> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root = File(external, "appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(external.toPath()) && root != external)
        root.deleteRecursively()
        val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val cards = CardRepository(app, stickers)
        cards.create(card)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PageScaffold("编辑拍立得", { backs++ }, largeBar = false) {
                CardEditorScreen(cards, stickers, card.id, { messages += it })
            }
        } }
        return cards to stickers
    }

    private fun paperBounds(card: PolaroidCard): Rect = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().boundsInRoot

    private fun assertPaperAboveSheet(card: PolaroidCard, screenshot: String): Rect {
        val paper = paperBounds(card)
        val sheet = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Paper must leave room above the real native sheet", paper.bottom < sheet.top)
        val output = File("build/previews/$screenshot.png").apply { parentFile.mkdirs() }
        val root = compose.onRoot()
        val window = root.fetchSemanticsNode().boundsInRoot
        root.captureRoboImage(output.absolutePath)
        val image = output.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            val x = ((paper.center.x - window.left) / window.width * image.width).toInt().coerceIn(0, image.width - 1)
            val y = ((paper.bottom - paper.height * .03f - window.top) / window.height * image.height).toInt().coerceIn(0, image.height - 1)
            val pixel = image.getPixel(x, y)
            assertTrue("The lower paper must really draw rather than be covered by the sheet", Color.red(pixel) > 180 && Color.green(pixel) < 80 && Color.blue(pixel) > 100)
        } finally { image.recycle() }
        return paper
    }

    @Test fun collapsingAndReopeningTheNativeSheetActuallyResizesVisiblePaperWithoutOccludingIt() {
        val card = PolaroidCard(title = "", frameColor = 0xFFFF00CCL)
        show(card)
        fun expandFully() {
            compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
                down(center); moveBy(Offset(0f, -300f * density), delayMillis = 600); advanceEventTime(300); up()
            }
        }
        expandFully()
        val expanded = assertPaperAboveSheet(card, "card-editor-expanded-paper")
        compose.onNodeWithTag("toggle-card-editor-panel").performTouchInput { click(center) }
        val collapsed = assertPaperAboveSheet(card, "card-editor-collapsed-paper")
        assertTrue("Folding the tools must make the paper visibly larger", collapsed.width > expanded.width * 1.1f)
        assertTrue(collapsed.height > expanded.height * 1.1f)
        compose.onNodeWithTag("toggle-card-editor-panel").performTouchInput { click(center) }
        expandFully()
        val reopened = assertPaperAboveSheet(card, "card-editor-reopened-paper")
        assertEquals(expanded.width, reopened.width, 3f)
        assertEquals(expanded.height, reopened.height, 3f)
    }

    @Test fun photoTextSectionIsCollapsedByDefaultAndSavingAnEmptyNameRetainsTheCaption() {
        val original = PolaroidCard(title = "删除这个名字")
        val (cards, _) = show(original)
        compose.onNodeWithTag("card-title").assertDoesNotExist()
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        compose.onNodeWithTag("card-title").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("card-description").performScrollTo().performTextReplacement("照片说明仍存在")
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(original, cards.cards.value.single())
        compose.onNodeWithTag("save-card").assertIsEnabled().performClick()
        compose.waitUntil(8_000) { backs == 1 }
        assertEquals("", cards.cards.value.single().title)
        assertEquals("照片说明仍存在", cards.cards.value.single().description)
        assertEquals(listOf("已保存拍立得"), messages)
    }

    @Test fun addingTextEditsInsideTheExistingSheetAndSavingItsEmptyValueDropsOnlyThatLayer() {
        val original = PolaroidCard(title = "")
        val (cards, _) = show(original)
        compose.onNodeWithTag("card-editor-tab/STICKERS").performClick()
        compose.onNodeWithText("添加文字", substring = false).performClick()
        compose.onNodeWithTag("card-layer-text").performScrollTo().assertIsDisplayed()
            .assert(hasAnyAncestor(hasTestTag("card-editor-panel")))
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag("card-layer-text").performTextReplacement("内联编辑")
        assertEquals(original, cards.cards.value.single())
        compose.onNodeWithTag("card-layer-text").performTextReplacement("")
        compose.onNodeWithTag("save-card").assertIsEnabled().performClick()
        compose.waitUntil(8_000) { backs == 1 }
        assertTrue(cards.cards.value.single().layers.isEmpty())
        assertEquals("", cards.cards.value.single().title)
        assertEquals(listOf("已保存拍立得"), messages)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun backConfirmationCanSaveTheSameUnnamedCardAndLeaveOnlyAfterTheSuccessfulSave() {
        val original = PolaroidCard(title = "")
        val (cards, _) = show(original)
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        compose.onNodeWithTag("card-description").performScrollTo().performTextReplacement("返回时保存的说明")
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").assertIsEnabled().performClick()
        compose.waitUntil(8_000) { backs == 1 }
        assertEquals("", cards.cards.value.single().title)
        assertEquals("返回时保存的说明", cards.cards.value.single().description)
        assertEquals(listOf("已保存拍立得"), messages)
        compose.onNode(isDialog()).assertDoesNotExist()
    }
}
