package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.BackEventCompat
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.next.ui.navigation.PageScaffold
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private data class Fixture(val cards: CardRepository, val stickers: StickerRepository, val first: PolaroidCard, val next: PolaroidCard, val nextAlbum: String)
    private fun image(stickers: StickerRepository, color: Int): String {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "browse-fixture-" + color + ".png")
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return try { stickers.importImage(Uri.fromFile(file)).id } finally { file.delete() }
    }
    private fun fixture(): Fixture {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root = File(external, "appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(external.toPath()) && root != external); root.deleteRecursively()
        app.getSharedPreferences("contact-cards", 0).edit().putString("items", "[]").commit()
        app.getSharedPreferences("cards-view", 0).edit().clear().commit()
        val paths = PluginPaths(app); val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val cards = CardRepository(app, stickers)
        val first = cards.create(PolaroidCard(title = "蓝色照片", imageId = image(stickers, android.graphics.Color.BLUE)))
        cards.createAlbum("空相册", "album.empty")
        val nextAlbum = cards.createAlbum("旅行", "album.travel").id
        val next = cards.create(PolaroidCard(title = "红色照片", imageId = image(stickers, android.graphics.Color.RED)), nextAlbum)
        return Fixture(cards, stickers, first, next, nextAlbum)
    }
    private fun show(f: Fixture) {
        // These geometry/focus fixtures intentionally paint creation order; production inserts new cards at the front.
        f.cards.albums.value.forEach { album -> f.cards.orderCards(album.id, f.cards.cards.value.asReversed().filter { it.id in album.cardIds }.map { it.id }) }
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("拍立得", {}) {
        CardsScreen(f.cards, f.stickers, {}, {}, {})
    } } }
        expandFixtureDock()
    }
    private fun expandFixtureDock() {
        // Drag to the full detent; the arrow only opens or closes the sheet.
        compose.expandAlbumGroups()
        compose.waitForIdle()
    }
    private fun capture(name: String): File {
        var project = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (!File(project, "settings.gradle.kts").isFile) project = project.parentFile ?: error("Project root not found")
        val output = File(project, ".tools/polaroid-" + name + ".png"); output.parentFile.mkdirs()
        compose.onNodeWithTag("polaroid-home").captureRoboImage(output.absolutePath)
        return output
    }
    private fun paperPixel(output: File, id: String, xRatio: Float = .5f, yRatio: Float = .5f): Int {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
        try {
            val home = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
            val paper = compose.onNodeWithTag("polaroid/" + id).fetchSemanticsNode().layoutInfo.coordinates
            // A rotated, partly offscreen paper has a clipped axis-aligned semantics rectangle.
            // Percentages of that rectangle can land on the rear photograph instead of its margin.
            val point = paper.localToRoot(Offset(paper.size.width * xRatio, paper.size.height * yRatio))
            assertTrue("The sampled paper point must remain visible: " + point + " in " + home, home.contains(point))
            val x = ((point.x - home.left) / home.width * bitmap.width).toInt()
            val y = ((point.y - home.top) / home.height * bitmap.height).toInt()
            return bitmap.getPixel(x, y)
        } finally { bitmap.recycle() }
    }

    @Test fun shrinkingTheDensestGridShowsEveryAlbumAndOpeningAnAlbumRestoresItsDenseGrid() {
        val f = fixture()
        val secondCover = f.cards.create(PolaroidCard(title = "绿色照片", imageId = image(f.stickers, android.graphics.Color.GREEN)))
        val thirdCover = f.cards.create(PolaroidCard(title = "黄色照片", imageId = image(f.stickers, android.graphics.Color.YELLOW)))
        show(f)
        fun pinch(expand: Boolean) {
            compose.onNodeWithTag("polaroid-browser").performTouchInput {
                val start = if (expand) 30f else 90f
                val end = if (expand) 90f else 30f
                down(0, center - Offset(start, 0f)); down(1, center + Offset(start, 0f))
                moveTo(0, center - Offset(end, 0f)); moveTo(1, center + Offset(end, 0f))
                up(0); up(1)
            }
            compose.waitForIdle()
        }
        repeat(4) { pinch(false) }
        compose.onNodeWithTag("cards-album-overview").assertIsDisplayed()
        compose.onNodeWithTag("polaroid-album/" + CardRepository.DEFAULT_ALBUM).assertExists()
        compose.onNodeWithTag("polaroid-album/album.empty").assertIsDisplayed()
        val albumTag = "polaroid-album/" + CardRepository.DEFAULT_ALBUM
        fun coverAngle(id: String): Double {
            val cover = compose.onNode(hasTestTag("polaroid/" + id) and hasAnyAncestor(hasTestTag(albumTag)), useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().layoutInfo.coordinates
            val edge = cover.localToRoot(Offset(cover.size.width.toFloat(), 0f)) - cover.localToRoot(Offset.Zero)
            return Math.toDegrees(kotlin.math.atan2(edge.y.toDouble(), edge.x.toDouble()))
        }
        assertEquals(-2.0, coverAngle(f.first.id), .1)
        assertEquals(6.0, coverAngle(secondCover.id), .1)
        assertEquals(-8.0, coverAngle(thirdCover.id), .1)
        val colors = listOf(android.graphics.Color.BLUE, android.graphics.Color.GREEN, android.graphics.Color.YELLOW)
        var coverPixels: Map<Int, Int> = emptyMap()
        compose.waitUntil(8_000) {
            val output = capture("album-overview")
            val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            try {
                val home = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
                val album = compose.onNodeWithTag(albumTag).fetchSemanticsNode().boundsInRoot
                val left = ((album.left - home.left) / home.width * bitmap.width).toInt().coerceIn(0, bitmap.width)
                val right = ((album.right - home.left) / home.width * bitmap.width).toInt().coerceIn(left, bitmap.width)
                val top = ((album.top - home.top) / home.height * bitmap.height).toInt().coerceIn(0, bitmap.height)
                val bottom = ((album.bottom - home.top) / home.height * bitmap.height).toInt().coerceIn(top, bitmap.height)
                val counts = colors.associateWith { 0 }.toMutableMap()
                for (y in top until bottom) for (x in left until right) {
                    val pixel = bitmap.getPixel(x, y)
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)
                    // The opaque front paper casts a real shadow over its rear photos.
                    // Preserve their saturated hue while allowing that luminance reduction.
                    val color = when {
                        blue >= 220 && red <= 8 && green <= 8 -> android.graphics.Color.BLUE
                        green >= 220 && red <= 8 && blue <= 8 -> android.graphics.Color.GREEN
                        red >= 220 && green >= 220 && blue <= 8 && kotlin.math.abs(red - green) <= 8 -> android.graphics.Color.YELLOW
                        else -> null
                    }
                    if (color != null) counts[color] = counts.getValue(color) + 1
                }
                coverPixels = counts
                colors.all { counts.getValue(it) >= 12 }
            } finally { bitmap.recycle() }
        }
        assertTrue("All three differently angled cover photographs must visibly render in their album: " + coverPixels,
            colors.all { coverPixels.getValue(it) >= 12 })
        compose.onNodeWithTag("polaroid-album/album.empty").performClick()
        compose.onNodeWithText("从一张照片开始").assertIsDisplayed()
        compose.onNodeWithTag("card-album/album.empty").assertIsSelected()
        compose.onNodeWithTag("cards-columns").performClick()
        compose.onNodeWithTag("polaroid-album/" + f.nextAlbum).performScrollTo().performClick()
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "4 列"))
        compose.onNodeWithTag("polaroid/" + f.next.id).assertIsDisplayed()
        compose.onNodeWithTag("polaroid/" + f.first.id).assertDoesNotExist()
        pinch(false)
        compose.onNodeWithTag("cards-album-overview").assertExists()
        pinch(true)
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "4 列"))
        pinch(true); pinch(true); pinch(true)
        compose.onNodeWithTag("cards-stack").assertExists()
        compose.onNodeWithTag("cards-position").assertTextEquals("4 / 4")
        compose.onNodeWithTag("card-album/" + f.nextAlbum).assertIsSelected()
    }

    @Test fun threePlanarPapersHaveTwoVisibleOpaqueRearMarginsAndDistinctAngles() {
        val f = fixture()
        val middle = f.cards.create(PolaroidCard(title = "绿色照片", imageId = image(f.stickers, android.graphics.Color.GREEN)))
        show(f)
        compose.onNodeWithTag("cards-position").assertTextEquals("1 / 3")
        fun angle(id: String): Double {
            val coordinates = compose.onNodeWithTag("polaroid/" + id).fetchSemanticsNode().layoutInfo.coordinates
            val edge = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f)) - coordinates.localToRoot(Offset.Zero)
            return Math.toDegrees(kotlin.math.atan2(edge.y.toDouble(), edge.x.toDouble()))
        }
        assertEquals(0.0, angle(f.first.id), .1)
        assertEquals(2.5, angle(middle.id), .1)
        assertEquals(5.0, angle(f.next.id), .1)
        val output = capture("three-planar-stack")
        listOf(middle.id, f.next.id).forEach { id ->
            val white = paperPixel(output, id, .97f, .43f)
            assertTrue("Both rear paper margins must be visible and opaque: " + id + "=" + white,
                android.graphics.Color.red(white) >= 248 && android.graphics.Color.green(white) >= 248 && android.graphics.Color.blue(white) >= 248)
        }
    }

    private fun rapidlyReverseDock(f: Fixture, name: String) {
        compose.waitUntil(8_000) { android.graphics.Color.blue(paperPixel(capture(name + "-ready"), f.first.id)) > 204 }
        compose.mainClock.autoAdvance = false
        val sizes = mutableSetOf<androidx.compose.ui.unit.IntSize>()
        var tick = 0
        fun frame() {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            tick++
            val paper = compose.onNodeWithTag("polaroid/" + f.first.id).fetchSemanticsNode()
            val coordinates = paper.layoutInfo.coordinates
            sizes += coordinates.size
            val viewport = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
            val top = coordinates.localToRoot(Offset.Zero)
            val bottom = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            assertTrue("A resizing paper must remain inside the page's safe drawing area: " + top + " .. " + bottom + " in " + viewport,
                top.x >= viewport.left - 1f && top.y >= viewport.top - 1f && bottom.x <= viewport.right + 1f && bottom.y <= viewport.bottom + 1f)
            // The endpoint only allocates layout space. The rising dock's mask, rather than
            // that new layout boundary, must cover the old paper gradually. Compare the real
            // clipped semantics bounds to its fully transformed corners to detect truncation.
            val visible = paper.boundsInRoot
            assertEquals("The real pager clip must retain the painted paper's left edge", top.x, visible.left, 1f)
            assertEquals("The real pager clip must retain the painted paper's top edge", top.y, visible.top, 1f)
            assertEquals("The real pager clip must retain the painted paper's right edge", bottom.x, visible.right, 1f)
            assertEquals("The real pager clip must retain the painted paper's bottom edge", bottom.y, visible.bottom, 1f)
            if (tick <= 16 && tick % 4 == 0) {
                val pixel = paperPixel(capture(name + "-mid-" + tick), f.first.id)
                assertTrue("The loaded photograph must stay visible during reversal: " + pixel,
                    android.graphics.Color.blue(pixel) > 204 && android.graphics.Color.red(pixel) < 38)
            }
        }
        repeat(4) {
            val before = compose.onNodeWithTag("polaroid/" + f.first.id).fetchSemanticsNode().layoutInfo.coordinates
            val beforeCenter = before.localToRoot(Offset(before.size.width / 2f, before.size.height / 2f))
            val beforeWidth = (before.localToRoot(Offset(before.size.width.toFloat(), 0f)) - before.localToRoot(Offset.Zero)).getDistance()
            compose.onNodeWithTag("toggle-card-albums").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.waitForIdle()
            val first = compose.onNodeWithTag("polaroid/" + f.first.id).fetchSemanticsNode().layoutInfo.coordinates
            val firstCenter = first.localToRoot(Offset(first.size.width / 2f, first.size.height / 2f))
            val firstWidth = (first.localToRoot(Offset(first.size.width.toFloat(), 0f)) - first.localToRoot(Offset.Zero)).getDistance()
            assertEquals("The new pager endpoint must preserve its first painted paper width", beforeWidth, firstWidth, 1.5f)
            assertEquals("The new pager endpoint must preserve its first painted paper center", beforeCenter.y, firstCenter.y, 1.5f)
            paperPixel(capture(name + "-first-" + it), f.first.id)
            repeat(4) { frame() }
        }
        val endpointSizes = sizes.toSet()
        repeat(20) { frame() }
        assertEquals("Only endpoint constraints may resize the canvas", endpointSizes, sizes)
        assertTrue("Three dock detents use at most three canvas sizes: " + sizes, sizes.size <= 3)
        // Also expand from a completely settled large paper: short reversals alone can miss
        // the old painted bottom being cut by the new, smaller pager's clip on its first frame.
        compose.onNodeWithTag("toggle-card-albums").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        repeat(20) { frame() }
        compose.onNodeWithTag("toggle-card-albums").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        frame()
        capture(name + "-full-expansion-first")
        repeat(20) { frame() }
        compose.onNodeWithTag("cards-position").assertTextEquals("1 / 2")
        compose.mainClock.autoAdvance = true
        // Button toggles return to the photo detent. Drag farther to inspect album selection.
        compose.expandAlbumGroups()
        compose.onNodeWithTag("card-album/" + CardRepository.DEFAULT_ALBUM).assertIsSelected()
    }

    @Test fun rapidlyReversingTheRealPortraitPagerDockKeepsMiniPaperUnclipped() {
        val f = fixture(); f.cards.update(f.first.copy(frameStyleId = "instax.mini.classic")); show(f)
        rapidlyReverseDock(f, "portrait-real-resize")
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun rapidlyReversingTheRealLandscapePagerDockKeepsMiniPaperUnclipped() {
        val f = fixture(); f.cards.update(f.first.copy(frameStyleId = "instax.mini.classic"))
        compose.setContent { KigttsTheme(ThemeSettings()) { CardPageScaffold("拍立得", {}) { CardsScreen(f.cards, f.stickers, {}, {}, {}) } } }
        expandFixtureDock()
        rapidlyReverseDock(f, "landscape-real-resize")
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w600dp-h550dp-xxhdpi")
    fun aNearlySquareWideWindowUsesTheRightDockAndVerticalRails() {
        val f = fixture()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(600.dp, 550.dp)) { CardsScreen(f.cards, f.stickers, {}, {}, {}) }
        } }
        expandFixtureDock()
        val browser = compose.onNodeWithTag("polaroid-browser").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("card-album-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.left >= browser.right)
        compose.onNodeWithTag("card-albums").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "竖向滚动"))
        compose.onNodeWithTag("card-album-photos").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "竖向滚动"))
    }

    @Test fun stackSkipsEmptyAlbumAndDialogPredictiveReturnRetainsTheFocusedPhoto() {
        val f = fixture(); show(f)
        compose.onNodeWithTag("cards-position").assertTextEquals("1 / 2")
        var foreground = android.graphics.Color.TRANSPARENT
        compose.waitUntil(8_000) {
            val output = capture("portrait-stack")
            foreground = paperPixel(output, f.first.id)
            android.graphics.Color.blue(foreground) > 204 && android.graphics.Color.red(foreground) < 38
        }
        assertTrue("The foreground blue photograph must cover the rear red card: " + foreground,
            android.graphics.Color.blue(foreground) > 204 && android.graphics.Color.red(foreground) < 38)
        compose.onNodeWithTag("cards-stack").performTouchInput {
            down(center); moveTo(center - Offset(width * .1f, 0f)); moveTo(center - Offset(width * .28f, 0f))
        }
        val mid = capture("portrait-turn-mid")
        // The front paper's right margin overlaps the rear red photograph while turning.
        // Its white pixels must stay white, rather than blend with that photograph or caption.
        val white = paperPixel(mid, f.first.id, .97f, .43f)
        assertTrue("The front paper must remain opaque during a turn: " + white,
            android.graphics.Color.red(white) >= 248 && android.graphics.Color.green(white) >= 248 && android.graphics.Color.blue(white) >= 248)
        compose.onNodeWithTag("cards-stack").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 2")
        compose.onNodeWithTag("card-album/" + f.nextAlbum).assertIsSelected()
        compose.onNodeWithTag("polaroid/" + f.next.id).performClick()
        compose.onNodeWithTag("card-preview-content").assertIsDisplayed()
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnIdle {
            dialog.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT))
            dialog.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(70f, 200f, .5f, BackEventCompat.EDGE_LEFT))
        }
        compose.runOnIdle { dialog.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithTag("card-preview-content").assertIsDisplayed()
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("card-preview-content").assertDoesNotExist()
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 2")
        compose.onNodeWithTag("card-album/" + f.nextAlbum).assertIsSelected()
    }

    @Test fun planarStackKeepsItsForegroundOnBothSidesOfTheHalfPageAndReturnsWithoutASortingFlash() {
        val f = fixture(); show(f)
        compose.onNodeWithTag("cards-stack").performTouchInput {
            down(center); moveTo(center - Offset(width * .1f, 0f)); moveTo(center - Offset(width * .48f, 0f))
        }
        fun whiteMargin(name: String) {
            val image = capture(name)
            val white = paperPixel(image, f.first.id, .97f, .43f)
            assertTrue("The outgoing paper keeps its drawing order: " + white,
                android.graphics.Color.red(white) >= 248 && android.graphics.Color.green(white) >= 248 && android.graphics.Color.blue(white) >= 248)
        }
        whiteMargin("2d-before-half")
        compose.onNodeWithTag("cards-stack").performTouchInput { moveTo(center - Offset(width * .56f, 0f)) }
        whiteMargin("2d-after-half")
        compose.onNodeWithTag("cards-stack").performTouchInput { up() }
        compose.waitForIdle(); compose.onNodeWithTag("cards-position").assertTextEquals("2 / 2")
        compose.onNodeWithTag("cards-stack").performTouchInput {
            down(center); moveTo(center + Offset(width * .1f, 0f)); moveTo(center + Offset(width * .54f, 0f))
        }
        capture("2d-return-mid")
        compose.onNodeWithTag("cards-stack").performTouchInput { up() }
        compose.waitForIdle(); compose.onNodeWithTag("cards-position").assertTextEquals("1 / 2")
        val blue = paperPixel(capture("2d-return-settled"), f.first.id)
        assertTrue(android.graphics.Color.blue(blue) > 204 && android.graphics.Color.red(blue) < 38)
    }

    @Test fun modeButtonsAndRealPinchesChangeGridDensityAndReturnToStack() {
        val f = fixture()
        val second = f.cards.create(PolaroidCard(title = "蓝色照片二", imageId = f.first.imageId))
        val third = f.cards.create(PolaroidCard(title = "蓝色照片三", imageId = f.first.imageId))
        show(f)
        compose.onNodeWithTag("cards-browse-mode").performClick()
        compose.onNodeWithTag("cards-grid").assertExists().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "2 列"))
        fun paper(id: String) = compose.onNode(hasTestTag("polaroid/" + id) and hasAnyAncestor(hasTestTag("cards-grid"))).fetchSemanticsNode().boundsInRoot
        assertTrue(paper(third.id).top >= paper(f.first.id).bottom)
        assertEquals(paper(f.first.id).top, paper(second.id).top, 1f)
        compose.onNodeWithTag("cards-columns").performClick()
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "3 列"))
        assertEquals(paper(f.first.id).top, paper(third.id).top, 1f)
        compose.onNodeWithTag("polaroid/" + f.next.id).assertDoesNotExist()
        fun pinch(expand: Boolean) {
            compose.onNodeWithTag("polaroid-browser").performTouchInput {
                val start = if (expand) 32f else 90f
                val end = if (expand) 90f else 32f
                down(0, center - Offset(start, 0f)); down(1, center + Offset(start, 0f))
                moveTo(0, center - Offset(end, 0f)); moveTo(1, center + Offset(end, 0f))
                up(0); up(1)
            }
            compose.waitForIdle()
        }
        pinch(true)
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "2 列"))
        pinch(true)
        compose.onNodeWithTag("cards-stack").assertExists(); compose.onNodeWithTag("cards-grid").assertDoesNotExist()
        pinch(false)
        compose.onNodeWithTag("cards-grid").assertExists()
        capture("portrait-grid")
    }

    @Test fun reopeningANonDefaultAlbumStartsAtItsPhotoAndGridFiltersByAlbum() {
        val f = fixture()
        ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("cards-view", 0)
            .edit().putString("album", f.nextAlbum).commit()
        show(f)
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 2")
        compose.onNodeWithTag("card-album/" + f.nextAlbum).assertIsSelected()
        compose.onNodeWithTag("cards-browse-mode").performClick()
        compose.onNodeWithTag("polaroid/" + f.next.id).assertIsDisplayed()
        compose.onNodeWithTag("polaroid/" + f.first.id).assertDoesNotExist()
        compose.onNodeWithTag("card-album/" + CardRepository.DEFAULT_ALBUM).performClick()
        compose.onNodeWithTag("polaroid/" + f.first.id).assertIsDisplayed()
        compose.onNodeWithTag("polaroid/" + f.next.id).assertDoesNotExist()
    }

    @Test fun liveInsertDeleteAndReorderKeepTheSameCardFocusedWithoutStalePagerKeys() {
        val f = fixture()
        val second = f.cards.create(PolaroidCard(title = "第二张"))
        val third = f.cards.create(PolaroidCard(title = "第三张"))
        show(f)
        compose.onNodeWithTag("cards-position").assertTextEquals("1 / 4")
        lateinit var inserted: PolaroidCard
        compose.runOnIdle { inserted = f.cards.create(PolaroidCard(title = "新照片")) }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 5")
        compose.runOnIdle { f.cards.orderCards(CardRepository.DEFAULT_ALBUM, listOf(inserted.id, third.id, second.id, f.first.id)) }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("4 / 5")
        compose.onNodeWithTag("card-album-photo/" + f.first.id).assertIsSelected()
        compose.runOnIdle { f.cards.remove(inserted.id) }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("3 / 4")
        compose.onNodeWithTag("card-album-photo/" + f.first.id).assertIsSelected()
        compose.runOnIdle { f.cards.remove(f.first.id) }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("1 / 3")
        compose.onNodeWithTag("card-album-photo/" + third.id).assertIsSelected()
    }

    @Test fun choosingAnotherAlbumDuringADragCancelsTheOldPageAndKeepsTheRequestedPhoto() {
        val f = fixture()
        val album = f.cards.createAlbum("人物", "album.people")
        val requested = f.cards.create(PolaroidCard(title = "人物照片"), album.id)
        show(f)
        compose.onNodeWithTag("cards-stack").performTouchInput {
            down(center); moveTo(center - Offset(width * .08f, 0f)); moveTo(center - Offset(width * .28f, 0f))
        }
        compose.onNodeWithTag("card-album/" + album.id).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithTag("cards-stack").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("3 / 3")
        compose.onNodeWithTag("card-album/" + album.id).assertIsSelected()
        compose.onNodeWithTag("card-album-photo/" + requested.id).assertIsSelected().assertIsDisplayed()
    }

    @Test fun removingAPreviewedCardDoesNotReopenTheDialogWhenItsIdIsRecreated() {
        val f = fixture(); show(f)
        compose.onNodeWithTag("polaroid/" + f.first.id).performClick()
        compose.onNodeWithTag("card-preview-content").assertIsDisplayed()
        compose.runOnIdle { f.cards.remove(f.first.id) }
        compose.waitForIdle()
        compose.onNodeWithTag("card-preview-content").assertDoesNotExist()
        compose.runOnIdle { f.cards.create(f.first) }
        compose.waitForIdle()
        compose.onNodeWithTag("card-preview-content").assertDoesNotExist()
    }

    @Test fun albumAndPhotoRailsFollowTheSelectedItemsAcrossTheirScrollableBounds() {
        val albums = (0..11).map { PolaroidAlbum("album/" + it, "相册 " + it) }
        val photos = (0..11).map { PolaroidCard(title = "照片 " + it) }
        var selected by mutableStateOf(albums.first().id)
        var focused by mutableStateOf(photos.first().id)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CardAlbumDock(albums, selected, photos, focused, { null }, true, false, true,
                {}, { selected = it }, { focused = it.id }, {}, {}, {})
        } }
        compose.runOnIdle { selected = albums.last().id; focused = photos.last().id }
        compose.waitForIdle()
        compose.onNodeWithTag("card-album/" + albums.last().id).assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("card-album-photo/" + photos.last().id).assertIsSelected().assertIsDisplayed()
        compose.runOnIdle { selected = albums.first().id; focused = photos.first().id }
        compose.waitForIdle()
        compose.onNodeWithTag("card-album/" + albums.first().id).assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("card-album-photo/" + photos.first().id).assertIsSelected().assertIsDisplayed()
    }

    @Test fun portraitAlbumDockCollapsesAtTheBottomWithoutLosingItsSelection() {
        val f = fixture(); show(f)
        val page = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
        val browser = compose.onNodeWithTag("polaroid-browser").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("card-album-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.top >= browser.bottom && dock.bottom <= page.bottom)
        compose.onNodeWithTag("card-album/" + f.nextAlbum).performClick()
        compose.onNodeWithTag("toggle-card-albums").performClick()
        compose.onNodeWithTag("card-album-photos").assertDoesNotExist()
        val collapsed = compose.onNodeWithTag("card-album-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(collapsed.height < dock.height)
        expandFixtureDock()
        compose.onNodeWithTag("card-album/" + f.nextAlbum).assertIsSelected()
        compose.onNodeWithTag("card-album-photo/" + f.next.id).assertIsSelected()
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun landscapeAlbumDockCollapsesOnTheRightAndKeepsThePhotoVisible() {
        val f = fixture()
        val second = f.cards.create(PolaroidCard(title = "另一张照片"))
        show(f)
        val page = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
        val browser = compose.onNodeWithTag("polaroid-browser").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("card-album-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.left >= browser.right && dock.right <= page.right)
        compose.onNodeWithTag("card-albums").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "竖向滚动"))
        compose.onNodeWithTag("card-album-photos").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "竖向滚动"))
        val one = compose.onNodeWithTag("card-album-photo/" + f.first.id).fetchSemanticsNode().boundsInRoot
        val two = compose.onNodeWithTag("card-album-photo/" + second.id).fetchSemanticsNode().boundsInRoot
        assertTrue(two.top >= one.bottom)
        capture("landscape-stack")
        compose.onNodeWithTag("toggle-card-albums").performClick()
        val collapsed = compose.onNodeWithTag("card-album-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(collapsed.width < dock.width)
        compose.onNodeWithTag("polaroid/" + f.first.id).assertIsDisplayed()
        capture("landscape-collapsed")
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w1280dp-h600dp-land-xxhdpi")
    fun aWideWindowShowsAStraightCarouselWithSmallerFadedNeighborsAndCompactRails() = exerciseWideCarousel(false)

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun mixedPaperFormatsKeepTheirMainPhotoClearThroughForwardAndReverseLandscapePaging() = exerciseWideCarousel(true)

    private fun exerciseWideCarousel(mixedFormats: Boolean) {
        val f = fixture()
        if (mixedFormats) f.cards.update(f.first.copy(frameStyleId = "instax.wide.classic"))
        val middle = f.cards.create(PolaroidCard(title = "绿色照片", imageId = image(f.stickers, android.graphics.Color.GREEN),
            frameStyleId = if (mixedFormats) "instax.mini.classic" else "instax.square.classic"))
        f.cards.orderCards(CardRepository.DEFAULT_ALBUM, listOf(f.first.id, middle.id))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CardPageScaffold("拍立得", {}) { CardsScreen(f.cards, f.stickers, {}, {}, {}) }
        } }
        expandFixtureDock()
        compose.onNodeWithTag("card-album-photo/" + middle.id).performClick()
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 3")
        fun coordinates(id: String) = compose.onNodeWithTag("polaroid/" + id).fetchSemanticsNode().layoutInfo.coordinates
        fun center(id: String): Offset {
            val paper = coordinates(id)
            return paper.localToRoot(Offset(paper.size.width / 2f, paper.size.height / 2f))
        }
        fun angle(id: String): Double {
            val paper = coordinates(id)
            val edge = paper.localToRoot(Offset(paper.size.width.toFloat(), 0f)) - paper.localToRoot(Offset.Zero)
            return Math.toDegrees(kotlin.math.atan2(edge.y.toDouble(), edge.x.toDouble()))
        }
        assertTrue(center(f.first.id).x < center(middle.id).x && center(middle.id).x < center(f.next.id).x)
        fun scale(id: String): Float {
            val paper = coordinates(id)
            return (paper.localToRoot(Offset(0f, paper.size.height.toFloat())).y - paper.localToRoot(Offset.Zero).y) / paper.size.height
        }
        val slot = center(f.next.id).x - center(middle.id).x
        listOf(f.first.id, middle.id, f.next.id).forEach { assertEquals(0.0, angle(it), .1) }
        assertTrue(scale(f.first.id) in .01f.. .841f); assertEquals(1f, scale(middle.id), .01f); assertTrue(scale(f.next.id) in .01f.. .841f)
        fun edge(id: String, right: Boolean): Float {
            val paper = coordinates(id)
            return paper.localToRoot(Offset(if (right) paper.size.width.toFloat() else 0f, 0f)).x
        }
        val gap = edge(f.next.id, false) - edge(middle.id, true)
        assertTrue(gap > 0f)
        assertEquals(gap, edge(middle.id, false) - edge(f.first.id, true), 2f)
        assertEquals(center(middle.id).y, center(f.first.id).y, 2f)
        assertEquals(center(middle.id).y, center(f.next.id).y, 2f)
        val albums = compose.onNodeWithTag("card-albums").fetchSemanticsNode().boundsInRoot
        val photos = compose.onNodeWithTag("card-album-photos").fetchSemanticsNode().boundsInRoot
        assertTrue(photos.right <= albums.left && kotlin.math.abs(albums.top - photos.top) <= 1f)
        compose.onNodeWithTag("card-albums").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "竖向滚动"))
        compose.onNodeWithTag("card-album-photos").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "竖向滚动"))
        val album = compose.onNodeWithTag("card-album/" + CardRepository.DEFAULT_ALBUM).getUnclippedBoundsInRoot()
        assertTrue("Landscape albums remain compact rows", album.bottom - album.top <= 48.dp)
        val thumbnail = compose.onNodeWithTag("card-album-photo/" + middle.id).getUnclippedBoundsInRoot()
        assertTrue(thumbnail.right - thumbnail.left <= 64.dp)
        assertEquals((if (mixedFormats) PolaroidFormat.MINI else PolaroidFormat.SQUARE).geometry.aspectRatio,
            ((thumbnail.right - thumbnail.left) / (thumbnail.bottom - thumbnail.top)).toDouble(), .01)
        compose.waitUntil(8_000) {
            val output = capture("wide-carousel-" + mixedFormats)
            val blue = paperPixel(output, f.first.id, .5f, .4f)
            val green = paperPixel(output, middle.id, .5f, .4f)
            val red = paperPixel(output, f.next.id, .5f, .4f)
            android.graphics.Color.blue(blue) >= 220 && android.graphics.Color.red(blue) in 90..165 &&
                android.graphics.Color.green(green) >= 220 && android.graphics.Color.blue(green) <= 8 &&
                android.graphics.Color.red(red) >= 220 && android.graphics.Color.green(red) in 90..165
        }
        compose.onNodeWithTag("cards-stack").performTouchInput {
            down(center); moveTo(center - Offset(width * .1f, 0f)); moveTo(center - Offset(width * .48f, 0f))
        }
        fun straightSlots(stage: String) {
            val output = capture(stage)
            listOf(middle.id, f.next.id).forEach { id -> listOf(.03f, .97f).forEach { edge ->
                val white = paperPixel(output, id, edge, .43f)
                assertTrue("Both papers retain visible, unoccluded margins while fading", android.graphics.Color.red(white) >= 220 &&
                    android.graphics.Color.green(white) >= 220 && android.graphics.Color.blue(white) >= 220)
            } }
            assertEquals("The two photos exchanging focus retain their small edge gap", gap, edge(f.next.id, false) - edge(middle.id, true), 2f)
            assertEquals(center(middle.id).y, center(f.next.id).y, 2f)
            listOf(middle.id, f.next.id).forEach { assertEquals(0.0, angle(it), .1); assertTrue(scale(it) in .01f..1.001f) }
            val left = compose.onNodeWithTag("polaroid/" + middle.id).fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("polaroid/" + f.next.id).fetchSemanticsNode().boundsInRoot
            assertTrue("Adjacent carousel papers never overlap", left.right < right.left)
        }
        straightSlots("wide-carousel-before-half-" + mixedFormats)
        compose.onNodeWithTag("cards-stack").performTouchInput { moveTo(center - Offset(width * .56f, 0f)) }
        straightSlots("wide-carousel-after-half-" + mixedFormats)
        compose.onNodeWithTag("cards-stack").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("3 / 3")
        compose.onNodeWithTag("card-album/" + f.nextAlbum).assertIsSelected()
        assertEquals(1f, scale(f.next.id), .01f); assertTrue(scale(middle.id) in .01f.. .841f)
        listOf(.03f, .97f).forEach { edge ->
            val white = paperPixel(capture("wide-settled-front"), f.next.id, edge, .43f)
            assertTrue(android.graphics.Color.red(white) >= 248 && android.graphics.Color.green(white) >= 248 && android.graphics.Color.blue(white) >= 248)
        }
        compose.onNodeWithTag("cards-stack").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 3")
        compose.onNodeWithTag("card-album/" + CardRepository.DEFAULT_ALBUM).assertIsSelected()
        assertEquals(1f, scale(middle.id), .01f); assertTrue(scale(f.next.id) in .01f.. .841f)
        assertEquals(slot, center(f.next.id).x - center(middle.id).x, 2f)
        capture("wide-carousel-return-" + mixedFormats)
    }
}
