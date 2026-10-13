package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.next.ui.navigation.PageScaffold
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
class CardWorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun repositories(): Pair<CardRepository, StickerRepository> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root = File(external, "appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(external.toPath()) && root != external); root.deleteRecursively()
        app.getSharedPreferences("contact-cards", 0).edit().putString("items", "[]").commit()
        app.getSharedPreferences("cards-view", 0).edit().clear().commit()
        val paths = PluginPaths(app); val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        return CardRepository(app, stickers) to stickers
    }
    @Test fun cardHomeUsesFabOnlyAndClickPreviewLongPressEditWithDiscoveryPreference() {
        val (cards, stickers) = repositories(); val messages = mutableListOf<String>()
        val card = cards.create(PolaroidCard(title = "拍立得")); val previews = mutableListOf<String>(); val edits = mutableListOf<String>(); var cameras = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("拍立得", {}) {
            CardsScreen(cards, stickers, { previews += it }, { edits += it }, { messages += it }, openCamera = { cameras++ })
        } } }
        compose.onNodeWithTag("cards-tabs").assertDoesNotExist()
        compose.onNodeWithTag("polaroid/" + card.id).performClick()
        assertEquals(listOf(card.id), previews)
        compose.onNodeWithTag("polaroid/" + card.id).performTouchInput { longClick() }
        assertEquals(listOf(card.id), edits)
        compose.onNodeWithTag("card-add-fab").performClick()
        compose.onNodeWithText("拍照").assertIsDisplayed(); compose.onNodeWithText("选图").assertIsDisplayed()
        compose.onNodeWithText("拍照").performClick()
        assertEquals(1, cameras)
        compose.onNodeWithTag("card-add-fab").performClick()
        compose.onNodeWithText("新建空白拍立得").performClick()
        compose.waitUntil(8_000) { edits.size == 2 }
        assertEquals(2, cards.cards.value.size)
        compose.onNodeWithTag("face-discovery").assertIsOff().performClick().assertIsOn()
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(app.getSharedPreferences("cards-view", 0).getBoolean("face-discovery", false))
        assertTrue(messages.last().contains("暂不搜索"))
    }
    @Test fun unsavedCardChangesStayLocalAndBackAsksBeforeSavingOnce() {
        val (cards, stickers) = repositories(); val original = cards.create(PolaroidCard(title = "旧标题"))
        var backs = 0; var library = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("编辑拍立得", { backs++ }) {
            CardEditorScreen(cards, stickers, original.id, {}, openLibrary = { library++ })
        } } }
        compose.onNodeWithTag("card-title").performScrollTo().performTextReplacement("新标题")
        assertEquals("旧标题", cards.cards.value.single().title)
        compose.onNodeWithTag("card-editor-tab/STICKERS").performScrollTo().performClick()
        compose.onNodeWithText("管理贴纸库").performScrollTo().performClick()
        assertEquals(1, library)
        compose.onNodeWithTag("card-title").assertTextContains("新标题")
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("save-card").performClick()
        compose.waitUntil(8_000) { backs == 1 }
        assertEquals("新标题", cards.cards.value.single().title)
    }
    @Test fun overlayDraggingChangesNormalizedDraftAndResizeKeepsItsPlacement() {
        val layer = CardLayer(kind = CardLayerKind.TEXT, text = "🙂", width = .08)
        var card by mutableStateOf(PolaroidCard(title = "拍立得", layers = listOf(layer)))
        var width by mutableStateOf(220.dp)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PolaroidCanvas(card, { null }, Modifier.width(width), layer.id, true, {}, { updated -> card = card.copy(layers = listOf(updated)) })
        } }
        compose.onNodeWithTag("polaroid/" + card.id).performTouchInput { swipe(center, center + Offset(70f, 0f), durationMillis = 400) }
        assertTrue(card.layers.single().x > .5)
        val saved = card.layers.single()
        compose.runOnIdle { width = 300.dp }
        assertEquals(saved, card.layers.single())
        compose.onNodeWithTag("card-layer-flip-x/" + layer.id).performClick()
        assertTrue(card.layers.single().flipX)
        val rotationBounds = compose.onNodeWithTag("card-layer-rotate/" + layer.id).fetchSemanticsNode().boundsInRoot
        val mirrorBounds = compose.onNodeWithTag("card-layer-flip-y/" + layer.id).fetchSemanticsNode().boundsInRoot
        assertTrue(rotationBounds.bottom <= mirrorBounds.top)
        val beforeRotation = card.layers.single()
        compose.onNodeWithTag("card-layer-rotate/" + layer.id).assertExists().performTouchInput {
            swipe(center, center + Offset(18f, -24f), durationMillis = 400)
        }
        val rotated = card.layers.single()
        assertTrue(kotlin.math.abs(rotated.rotation - beforeRotation.rotation) > 1.0)
        assertEquals(beforeRotation.x, rotated.x, 0.0)
        assertEquals(beforeRotation.y, rotated.y, 0.0)
        compose.onNodeWithTag("card-layer-scale/" + layer.id).assertExists().performClick()
        assertEquals(rotated.width * 1.1, card.layers.single().width, .000001)
    }
    @Test fun photoPinchStaysLocalUntilBothPointersAreReleasedAndSingleFingerDoesNotCrop() {
        val (cards, stickers) = repositories()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "card-photo-test.png")
        val bitmap = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = stickers.importImage(Uri.fromFile(file)); file.delete()
        val card = cards.create(PolaroidCard(title = "照片", imageId = image.id))
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("拍立得预览", {}) {
            CardPreviewScreen(cards, stickers, card.id, {}, {})
        } } }
        compose.waitUntil(8_000) { compose.onAllNodesWithContentDescription("拍立得照片").fetchSemanticsNodes().isEmpty() }
        val photo = compose.onNodeWithTag("card-photo/" + card.id)
        photo.performTouchInput { swipe(center, center + Offset(40f, 0f)) }
        assertEquals(1.0, cards.cards.value.single().photoZoom, 0.0)
        photo.performTouchInput {
            down(0, center - Offset(35f, 0f)); down(1, center + Offset(35f, 0f))
            moveBy(0, Offset(-25f, 0f)); moveBy(1, Offset(25f, 0f))
        }
        assertEquals(1.0, cards.cards.value.single().photoZoom, 0.0)
        photo.performTouchInput { up(0); up(1) }
        compose.waitUntil(8_000) { cards.cards.value.single().photoZoom > 1.05 }
    }
    @Test fun cameraResultReplacesOnlyPhotoAndFormatOfTheUnsavedDraft() {
        val (cards, stickers) = repositories()
        val original = cards.create(PolaroidCard(title = "原标题"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val source = File(app.cacheDir, "camera-result.png")
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = stickers.importImage(Uri.fromFile(source)); source.delete()
        var version by mutableLongStateOf(0L)
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("编辑拍立得", {}) {
            CardEditorScreen(cards, stickers, original.id, {}, cameraImageId = image.id,
                cameraOriginalImageId = image.id, cameraFrameStyleId = "instax.mini.classic", cameraRequestVersion = version)
        } } }
        compose.onNodeWithTag("card-title").performScrollTo().performTextReplacement("未保存标题")
        compose.runOnIdle { version = 1L }
        compose.onNodeWithTag("card-title").assertTextContains("未保存标题")
        assertEquals(original, cards.cards.value.single())
        compose.onNodeWithTag("save-card").performClick()
        compose.waitUntil(8_000) { cards.cards.value.single().imageId == image.id }
        val saved = cards.cards.value.single()
        assertEquals("未保存标题", saved.title)
        assertEquals(image.id, saved.originalImageId)
        assertEquals("instax.mini.classic", saved.frameStyleId)
    }
    @Test fun cropFormatChoiceStaysLocalAndPreviewUsesSharedPaperAndPhotoRatios() {
        val (cards, stickers) = repositories()
        val original = cards.create(PolaroidCard(title = "相纸"))
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("编辑拍立得", {}) {
            CardEditorScreen(cards, stickers, original.id, {})
        } } }
        compose.onNodeWithContentDescription("相纸规格：Wide").performScrollTo().performClick()
        assertEquals(original.frameStyleId, cards.cards.value.single().frameStyleId)
        compose.onNodeWithTag("polaroid/" + original.id).performScrollTo()
        val paper = compose.onNodeWithTag("polaroid/" + original.id).fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithTag("card-photo/" + original.id).fetchSemanticsNode().boundsInRoot
        assertEquals(PolaroidFormat.WIDE.geometry.aspectRatio, (paper.width / paper.height).toDouble(), .01)
        assertEquals(PolaroidFormat.WIDE.geometry.photoAspectRatio, (photo.width / photo.height).toDouble(), .01)
        compose.onNodeWithTag("save-card").performClick()
        compose.waitUntil(8_000) { cards.cards.value.single().frameStyleId == "instax.wide.classic" }
    }
    @Test fun qrStickerPreservesUnicodePayloadAndIsLocalUntilSave() {
        val (cards, stickers) = repositories()
        val original = cards.create(PolaroidCard(title = "二维码"))
        val messages = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("编辑拍立得", {}) {
            CardEditorScreen(cards, stickers, original.id, { messages += it })
        } } }
        compose.onNodeWithTag("card-editor-tab/STICKERS").performScrollTo().performClick()
        compose.onNodeWithTag("card-add-qr").performScrollTo().performClick()
        val payload = " https://example.test/你好?emoji=🙂 "
        compose.onNodeWithTag("card-name-input").performTextReplacement(payload)
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(8_000) { stickers.images.value.isNotEmpty() }
        assertTrue(cards.cards.value.single().layers.isEmpty())
        compose.waitUntil(8_000) { !compose.onNodeWithTag("save-card").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithTag("save-card").performClick()
        compose.waitUntil(8_000) { cards.cards.value.single().layers.size == 1 || messages.isNotEmpty() }
        assertEquals(messages.joinToString(), 1, cards.cards.value.single().layers.size)
        val layer = cards.cards.value.single().layers.single()
        assertEquals(CardLayerKind.IMAGE, layer.kind)
        assertEquals(payload, layer.text)
        assertNotNull(stickers.imageFile(requireNotNull(layer.imageId)))
    }
}
