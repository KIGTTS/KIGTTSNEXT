package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.CardLayerKind
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CardEditorSelectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun textInkKeepsItsSelectionWhileBothSameHeightEmptySidesAndPaperMarginDeselect() {
        val layer = CardLayer(kind = CardLayerKind.TEXT, text = "Hi", width = .07)
        var selected by mutableStateOf<String?>(layer.id)
        val card = PolaroidCard(title = "", layers = listOf(layer))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PolaroidCanvas(card, { null }, Modifier.width(330.dp), selected, true, { selected = it })
            }
        } }
        val paper = compose.onNodeWithTag("polaroid/" + card.id)
        val sticker = compose.onNodeWithTag("card-layer/" + layer.id, useUnmergedTree = true)
        val paperBounds = paper.fetchSemanticsNode().boundsInRoot
        val inkBounds = sticker.fetchSemanticsNode().boundsInRoot
        assertTrue("A short text's hit target must not span the paper", inkBounds.width < paperBounds.width / 2)
        sticker.performTouchInput { click(center) }
        assertEquals(layer.id, selected)
        val y = inkBounds.center.y - paperBounds.top
        for (x in listOf(paperBounds.width * .1f, paperBounds.width * .9f)) {
            paper.performTouchInput { click(Offset(x, y)) }
            assertNull("Empty paper beside the text must cancel adjustment", selected)
            sticker.performTouchInput { click(center) }
            assertEquals(layer.id, selected)
        }
        // This point is on the white paper to the left of the photo, not another sticker.
        paper.performTouchInput { click(Offset(width * .025f, height * .16f)) }
        assertNull(selected)
    }

    @Test fun actualMirrorAndRotationPointerInputChangesOnlyTheSelectedLayerAndNeverDeselects() {
        val layer = CardLayer(kind = CardLayerKind.TEXT, text = "Hi", width = .07)
        var card by mutableStateOf(PolaroidCard(title = "", layers = listOf(layer)))
        var selected by mutableStateOf<String?>(layer.id)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PolaroidCanvas(card, { null }, Modifier.width(330.dp), selected, true, { selected = it },
                    { update -> card = card.copy(layers = listOf(update)) })
            }
        } }
        compose.onNodeWithTag("card-layer-flip-x/" + layer.id, useUnmergedTree = true).performTouchInput { click(center) }
        assertTrue(card.layers.single().flipX)
        assertEquals(layer.id, selected)
        compose.onNodeWithTag("card-layer-flip-y/" + layer.id, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("card-layer-rotate/" + layer.id, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("card-layer-scale/" + layer.id, useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(20f, 35f), 100); up()
        }
        assertTrue("同一手柄改变缩放", card.layers.single().width != layer.width)
        assertTrue("同一手柄改变旋转", kotlin.math.abs(card.layers.single().rotation) > 1)
        assertEquals(layer.x, card.layers.single().x, 0.0)
        assertEquals(layer.y, card.layers.single().y, 0.0)
        assertEquals(layer.id, selected)
    }
}
