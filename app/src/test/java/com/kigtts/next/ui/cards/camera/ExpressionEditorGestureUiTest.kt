package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.kigtts.next.camera.expressions.EMPTY_EXPRESSION_SVG
import com.kigtts.next.camera.expressions.ExpressionEffect
import com.kigtts.next.camera.expressions.ExpressionLayer
import com.kigtts.next.camera.expressions.placement
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ExpressionEditorGestureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun previewSupportsDraggingPinchingAndThePhotoEditorMirrorHandle() {
        var effect by mutableStateOf(ExpressionEffect("test", "Test", listOf(ExpressionLayer(EMPTY_EXPRESSION_SVG))))
        compose.setContent {
            KigttsTheme(ThemeSettings()) {
                ExpressionArtPreview(effect, 0, true, {}, { _, layer ->
                    effect = effect.copy(layers = listOf(layer))
                }, {})
            }
        }
        val preview = compose.onNodeWithTag("expression-preview")
        fun stickerCenter(width: Int): Offset {
            val point = effect.layers.single().placement(expressionPreviewFace).center
            return Offset(point.x, point.y) * (width.toFloat() / EXPRESSION_PREVIEW_EDGE)
        }
        preview.performTouchInput {
            val start = stickerCenter(width)
            swipe(start, start + Offset(width * .09f, 0f))
        }
        assertTrue(effect.layers.single().x > .07f)
        preview.performTouchInput {
            val center = stickerCenter(width)
            val start = Offset(width * .08f, 0f)
            val end = Offset(width * .14f, width * .07f)
            down(0, center - start)
            down(1, center + start)
            moveTo(0, center - end)
            moveTo(1, center + end)
            up(0)
            up(1)
        }
        assertTrue(effect.layers.single().scale > 1.4f)
        assertTrue(effect.layers.single().rotation > 10f)
        compose.onNodeWithTag("card-layer-flip-x/expression-0", useUnmergedTree = true).performTouchInput { click(center) }
        assertTrue(effect.layers.single().flipX)
    }
}
