package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.camera.expressions.*
import com.kigtts.plugin.api.PluginJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpressionLibraryOrderTest {
    @Test fun defaultOrderCanBeRearrangedForBothConsumers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = ExpressionLibrary.get(context)
        val ids = library.choices().map { it.id }
        assertEquals(listOf("builtin.giggle", "builtin.not_giggle", "builtin.wise"), ids.take(3))
        assertEquals(listOf("builtin.agni", "builtin.side_eye", "builtin.kim", "builtin.lau"), ids.takeLast(4))

        val reordered = ids.drop(1) + ids.first()
        library.reorder(reordered)
        assertEquals(reordered, library.choices().map { it.id })
        assertEquals(reordered, com.kigtts.next.state.ManagementOrderStore(context).ordered("camera-face-stickers", ids))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun galleryImageIsEmbeddedInThePackAndRendersWithoutItsSourceFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("expression", ".png", context.cacheDir)
        val input = Bitmap.createBitmap(642, 321, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val layer = try {
            file.outputStream().use { input.compress(Bitmap.CompressFormat.PNG, 100, it) }
            expressionPhotoLayer(context, Uri.fromFile(file)).copy(scale = 1.2f)
        } finally {
            input.recycle()
            file.delete()
        }
        val effect = ExpressionEffect("sticker", "Sticker", listOf(layer))
        val exported = PluginJson.encodeToString(ExpressionPack(effects = listOf(effect)))
        val restored = ExpressionPack.read(exported).effects.single()
        assertEquals(layer, restored.layers.single())
        val result = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            CompiledExpression.compile(restored).draw(Canvas(result), listOf(ExpressionFace.SAMPLE))
            val green = result.getPixel(160, 160)
            assertEquals(255, Color.alpha(green))
            org.junit.Assert.assertTrue(Color.green(green) > 225)
            assertEquals(0, Color.alpha(result.getPixel(160, 85)))
        } finally { result.recycle() }
    }

    @Test fun movingScalingAndRotatingAnEyeStickerKeepsItsVisualCenterUnderTheGesture() {
        val layer = ExpressionLayer(EMPTY_EXPRESSION_SVG, anchor = ExpressionAnchor.EYES)
        val center = layer.placement(expressionPreviewFace).center
        val moved = layer.transform(expressionPreviewFace, ExpressionPoint(11f, -7f), 1.5f, 27f)
        val movedCenter = moved.placement(expressionPreviewFace).center
        assertEquals(center.x + 11f, movedCenter.x, .001f)
        assertEquals(center.y - 7f, movedCenter.y, .001f)
        assertEquals(1.5f, moved.scale, .001f)
        assertEquals(27f, moved.rotation, .001f)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun wholeStickerBlurOverridesOldLayerFlagsAndRemovesPortraitFeatures() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val legacyLayer = ExpressionLayer(EMPTY_EXPRESSION_SVG, blurBase = true)
        val legacy = ExpressionEffect("test", "Test", listOf(legacyLayer))
        val serialized = PluginJson.encodeToString(ExpressionPack(effects = listOf(legacy)))
        assertTrue(ExpressionPack.read(serialized).effects.single().blurFaceEnabled)
        assertFalse(CompiledExpression.compile(legacy.copy(blurFace = false)).samplesSurface)
        assertTrue(CompiledExpression.compile(legacy.copy(blurFace = false,
            layers = listOf(legacyLayer.copy(hideUnderFringe = true)))).samplesSurface)
        val image = Bitmap.createBitmap(EXPRESSION_PREVIEW_EDGE, EXPRESSION_PREVIEW_EDGE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            drawExpressionPortrait(canvas, ExpressionPreviewPortrait.load(assets))
            val eye = image.getPixel(105, 177)
            val hood = image.getPixel(150, 55)
            val brow = image.getPixel(101, 91)
            image.eraseColor(Color.TRANSPARENT)
            drawExpressionPortrait(canvas, ExpressionPreviewPortrait.load(assets, withoutFeatures = true))
            assertTrue(Color.luminance(image.getPixel(105, 177)) > Color.luminance(eye))
            assertTrue(Color.luminance(image.getPixel(101, 91)) > Color.luminance(brow))
            assertEquals(hood, image.getPixel(150, 55))
            val fringeShadow = image.getPixel(141, 169)
            val uncoveredSkin = image.getPixel(94, 138)
            assertTrue(Color.green(fringeShadow) + 24 < Color.green(uncoveredSkin))
            assertTrue(kotlin.math.abs(Color.green(uncoveredSkin) - Color.green(image.getPixel(80, 155))) <= 7)
            assertTrue(kotlin.math.abs(Color.green(uncoveredSkin) - Color.green(image.getPixel(222, 145))) <= 7)
        } finally { image.recycle() }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun grayscalePresetsUseTheWholeFacePlacementAndSaveInPlace() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val library = ExpressionLibrary.get(ctx)
        listOf("agni", "side_eye", "kim", "lau").forEach { name ->
            val layer = requireNotNull(library.find("builtin.$name")).layers.single()
            assertEquals(ExpressionAnchor.FACE, layer.anchor)
            assertEquals(.95f, layer.scale, .001f)
            assertEquals(.05f, layer.y, .001f)
        }
        val preset = requireNotNull(library.find("builtin.agni"))
        val original = preset.layers.single()
        val asset = renderExpressionArtwork(original.svg, null)
        assertEquals(320, asset.width)
        assertEquals(320, asset.height)
        assertTrue((0 until 320).any { y -> (0 until 320).any { x -> Color.alpha(asset.getPixel(x, y)) > 128 } })
        val editable = library.editable(preset)
        val layer = editable.layers.single()
        assertEquals(EMPTY_EXPRESSION_SVG, layer.svg)
        assertTrue(!layer.bitmapWebp.isNullOrBlank())
        assertEquals(original.placement(expressionPreviewFace), layer.placement(expressionPreviewFace))
        assertTrue(editable.blurFaceEnabled)
        assertEquals(preset.id, editable.id)
        val updated = editable.copy(name = "Edited")
        library.save(updated)
        assertEquals(updated, library.find(preset.id))
        assertEquals(1, library.choices().count { it.id == preset.id })
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun avatarBackdropIsMutedInTheEditorWithoutDimmingTheEffect() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val portrait = ExpressionPreviewPortrait.load(context.assets)
        val layer = ExpressionLayer(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 320"><circle cx="160" cy="160" r="40" fill="#FF0000"/></svg>""",
            anchor = ExpressionAnchor.NOSE,
        )
        val effect = CompiledExpression.compile(ExpressionEffect("test", "Test", listOf(layer)))
        val scrim = Paint().apply { color = Color.WHITE; alpha = (255 * EXPRESSION_PORTRAIT_SCRIM).toInt() }
        val image = Bitmap.createBitmap(EXPRESSION_PREVIEW_EDGE, EXPRESSION_PREVIEW_EDGE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        try {
            drawExpressionPortrait(canvas, portrait)
            val original = image.getPixel(105, 177)
            assertEquals(255, Color.alpha(original))
            drawExpressionPortrait(canvas, portrait, scrim)
            val muted = image.getPixel(105, 177)
            org.junit.Assert.assertTrue(Color.luminance(muted) > Color.luminance(original))
            effect.draw(canvas, listOf(expressionPreviewFace))
            val nose = expressionPreviewFace.nose
            assertEquals(Color.RED, image.getPixel(nose.x.toInt(), nose.y.toInt()))
        } finally { image.recycle() }
    }
}
