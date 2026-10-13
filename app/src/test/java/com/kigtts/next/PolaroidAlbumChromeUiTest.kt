package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
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
class PolaroidAlbumChromeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Fixture {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root = File(external, "appdata/Kigtts/cards").canonicalFile
        init {
            check(root.toPath().startsWith(external.toPath()) && root != external); root.deleteRecursively()
            app.getSharedPreferences("contact-cards", 0).edit().putString("items", "[]").commit()
            app.getSharedPreferences("cards-view", 0).edit().clear().commit()
        }
        val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val cards = CardRepository(app, stickers)
        fun photo(color: Int, album: String = CardRepository.DEFAULT_ALBUM): PolaroidCard {
            val source = File(app.cacheDir, "chrome-$color.png")
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            try { source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            val image = try { stickers.importImage(Uri.fromFile(source)) } finally { source.delete() }
            return cards.create(PolaroidCard(title = "", imageId = image.id), album)
        }
    }

    @Test fun albumsStartFoldedAndTheirExpansionCompactsThePortraitTitleWithHomeSizedCircleButtons() {
        val f = Fixture(); f.photo(Color.BLUE)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(400.dp, 780.dp)) { CardPageScaffold("拍立得", {}) {
                CardsScreen(f.cards, f.stickers, {}, {}, {})
            } }
        } }
        compose.onNodeWithTag("card-add-fab").assertDoesNotExist()
        compose.onNodeWithText("新建空白拍立得").assertDoesNotExist()
        compose.onNodeWithTag("card-albums").assertDoesNotExist()
        compose.onNodeWithTag("card-album-photos").assertDoesNotExist()
        val fullBar = compose.onNodeWithTag("large-page-bar").getUnclippedBoundsInRoot()
        val folded = compose.onNodeWithTag("card-album-dock").getUnclippedBoundsInRoot()
        assertEquals(60f, (folded.bottom - folded.top).value, .5f)
        compose.onNodeWithTag("toggle-card-albums").performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("card-albums").assertIsDisplayed()
        val compactBar = compose.onNodeWithTag("large-page-bar").getUnclippedBoundsInRoot()
        assertTrue("Expanded albums must collapse the large title", fullBar.bottom - fullBar.top > compactBar.bottom - compactBar.top + 48.dp)
        val title = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("page-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(title) }
        assertEquals(1, title.single().lineCount)
        listOf("toggle-card-albums", "create-card-album", "card-album-menu").forEach { tag ->
            val button = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals("$tag matches the expanded quick-text side button", 32f, (button.right - button.left).value, .5f)
            assertEquals(32f, (button.bottom - button.top).value, .5f)
        }
        compose.onNodeWithTag("toggle-card-albums").performTouchInput { click(center) }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("large-page-bar").getUnclippedBoundsInRoot()
        assertEquals((fullBar.bottom - fullBar.top).value, (restored.bottom - restored.top).value, .5f)
        compose.onNodeWithTag("card-albums").assertDoesNotExist()
    }

    @Test fun repeatedPortraitLandscapeChangesRetainTheBrowserItsFocusedAlbumAndActualPhotoPixels() {
        val f = Fixture(); f.photo(Color.BLUE)
        val album = f.cards.createAlbum("旅行", "rotation.travel")
        val red = f.photo(Color.RED, album.id)
        var width by mutableStateOf(400.dp); var height by mutableStateOf(780.dp)
        var created = 0; var disposed = 0
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(width, height)) { CardPageScaffold("拍立得", {}) {
                DisposableEffect(Unit) { created++; onDispose { disposed++ } }
                CardsScreen(f.cards, f.stickers, {}, {}, {})
            } }
        } }
        compose.onNodeWithTag("toggle-card-albums").performClick()
        compose.onNodeWithTag("card-album/" + album.id).performClick()
        compose.onNodeWithTag("cards-position").assertTextEquals("2 / 2")
        fun actualRedPaper(stage: String) {
            compose.onNodeWithTag("polaroid/" + red.id).assertIsDisplayed()
            val output = File("build/previews/polaroid-rotation-$stage.png").apply { parentFile.mkdirs() }
            var pixel = Color.TRANSPARENT
            compose.waitUntil(8_000) {
                compose.onNodeWithTag("polaroid-home").captureRoboImage(output.absolutePath)
                val image = output.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
                try {
                    val home = compose.onNodeWithTag("polaroid-home").fetchSemanticsNode().boundsInRoot
                    val paper = compose.onNodeWithTag("polaroid/" + red.id).fetchSemanticsNode().layoutInfo.coordinates
                    val point = paper.localToRoot(Offset(paper.size.width / 2f, paper.size.height * .4f))
                    assertTrue("The focused paper remains in the rotated viewport", home.contains(point))
                    val x = ((point.x - home.left) / home.width * image.width).toInt().coerceIn(0, image.width - 1)
                    val y = ((point.y - home.top) / home.height * image.height).toInt().coerceIn(0, image.height - 1)
                    pixel = image.getPixel(x, y)
                    Color.red(pixel) >= 220 && Color.blue(pixel) <= 8 && Color.green(pixel) <= 8
                } finally { image.recycle() }
            }
            assertTrue("The actual decoded photograph, rather than an empty placeholder, must survive $stage: $pixel", Color.red(pixel) >= 220)
            assertEquals(1, created); assertEquals(0, disposed)
            compose.onNodeWithTag("cards-position").assertTextEquals("2 / 2")
        }
        actualRedPaper("initial")
        repeat(3) { cycle ->
            compose.runOnIdle { width = 850.dp; height = 400.dp }
            compose.waitForIdle()
            compose.onNodeWithTag("small-page-bar").assertIsDisplayed()
            compose.onNodeWithTag("card-album/" + album.id).assertIsSelected()
            actualRedPaper("wide-$cycle")
            compose.onNodeWithTag("toggle-card-albums").performClick()
            compose.runOnIdle { width = 400.dp; height = 780.dp }
            compose.waitForIdle()
            actualRedPaper("tall-folded-$cycle")
            compose.onNodeWithTag("toggle-card-albums").performClick()
            compose.onNodeWithTag("card-album/" + album.id).assertIsSelected()
            actualRedPaper("tall-expanded-$cycle")
        }
    }
}
