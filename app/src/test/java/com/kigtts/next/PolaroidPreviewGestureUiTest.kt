package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardPreviewDialog
import com.kigtts.plugin.api.*
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
class PolaroidPreviewGestureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun pinchingAndPanningScaleTheWholePaperCaptionAndLogoWithoutChangingCropOrRevision() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val storage = File(external, "appdata/Kigtts/cards").canonicalFile
        check(storage.toPath().startsWith(external.toPath()) && storage != external); storage.deleteRecursively()
        val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val cards = CardRepository(app, stickers)
        val card = cards.create(PolaroidCard(title = "整体缩放", description = "裁剪保持原样", photoX = .31, photoY = .64, photoZoom = 2.3))
        val revision = stickers.snapshot.value.revision
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CardPreviewDialog(card, stickers::imageFile, stickers.frameStyle(card), {}, {}, {}, {}, {}, {})
        } }
        fun size(node: SemanticsNodeInteraction): Offset {
            val coordinates = node.fetchSemanticsNode().layoutInfo.coordinates
            return coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())) -
                coordinates.localToRoot(Offset.Zero)
        }
        fun paper() = compose.onNodeWithTag("polaroid/" + card.id)
        fun center(): Offset {
            val coordinates = paper().fetchSemanticsNode().layoutInfo.coordinates
            return coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
        }
        fun pinch(scale: Float) {
            compose.onNodeWithTag("card-preview-content").performTouchInput {
                down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
                moveTo(0, center - Offset(30f * scale, 0f)); moveTo(1, center + Offset(30f * scale, 0f))
                up(0); up(1)
            }
            compose.waitForIdle()
        }
        val paperSize = size(paper()); val originalCenter = center()
        val captionSize = size(compose.onNodeWithText(card.title, useUnmergedTree = true))
        val logoSize = size(compose.onNodeWithTag("polaroid-brand-logo", useUnmergedTree = true))
        pinch(2f)
        val enlarged = size(paper())
        assertEquals(2f, enlarged.x / paperSize.x, .015f)
        assertEquals(2f, enlarged.y / paperSize.y, .015f)
        val enlargedCaption = size(compose.onNodeWithText(card.title, useUnmergedTree = true))
        val enlargedLogo = size(compose.onNodeWithTag("polaroid-brand-logo", useUnmergedTree = true))
        assertEquals(2f, enlargedCaption.x / captionSize.x, .015f)
        assertEquals(2f, enlargedCaption.y / captionSize.y, .015f)
        assertEquals(2f, enlargedLogo.x / logoSize.x, .015f)
        assertEquals(2f, enlargedLogo.y / logoSize.y, .015f)
        val beforePan = center()
        compose.onNodeWithTag("card-preview-content").performTouchInput { swipe(center, center + Offset(80f, -90f), 200) }
        compose.waitForIdle()
        val moved = center()
        assertTrue(moved.x > beforePan.x + 50f && moved.y < beforePan.y - 60f)
        compose.onNodeWithTag("card-preview-window").captureRoboImage("build/previews/polaroid-preview-zoomed.png")
        pinch(.1f)
        assertEquals(paperSize.x, size(paper()).x, 1.5f)
        assertEquals(originalCenter.x, center().x, 1.5f)
        assertEquals(originalCenter.y, center().y, 1.5f)
        pinch(5f)
        assertEquals(4f, size(paper()).x / paperSize.x, .015f)
        compose.onNodeWithTag("reset-card-preview").performClick()
        assertEquals(paperSize.x, size(paper()).x, 1.5f)
        assertEquals(originalCenter.x, center().x, 1.5f)
        assertEquals(originalCenter.y, center().y, 1.5f)
        assertEquals(card, cards.cards.value.single())
        assertEquals(revision, stickers.snapshot.value.revision)
    }
}
