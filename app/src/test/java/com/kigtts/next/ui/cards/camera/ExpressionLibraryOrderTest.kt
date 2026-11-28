package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.camera.expressions.*
import com.kigtts.plugin.api.PluginJson
import org.junit.Assert.assertEquals
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
    fun libraryImageIsEmbeddedInThePackAndRendersWithoutItsSourceFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("expression", ".png", context.cacheDir)
        val input = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val encoded = try {
            file.outputStream().use { input.compress(Bitmap.CompressFormat.PNG, 100, it) }
            expressionStickerImage(file)
        } finally {
            input.recycle()
            file.delete()
        }
        val layer = ExpressionLayer(EMPTY_EXPRESSION_SVG, scale = 1.2f, bitmapWebp = encoded)
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
}
