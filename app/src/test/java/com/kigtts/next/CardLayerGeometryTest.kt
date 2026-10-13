package com.kigtts.next

import com.kigtts.next.ui.cards.transformCardLayer
import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.CardLayerKind
import org.junit.Assert.*
import org.junit.Test

class CardLayerGeometryTest {
    @Test fun equalPhysicalCanvasFractionsGiveEqualSavedCoordinatesAtDifferentPreviewSizes() {
        val layer = CardLayer(kind = CardLayerKind.TEXT, text = "贴纸文字", width = .07)
        val small = transformCardLayer(layer, 20f, -25f, 1.2f, 30f, 200f, 250f)
        val large = transformCardLayer(layer, 40f, -50f, 1.2f, 30f, 400f, 500f)
        assertEquals(small, large); assertEquals(.6, small.x, .00001); assertEquals(.4, small.y, .00001)
    }
    @Test fun longGesturesStayWithinWritableBoundsAndNormalizeRotation() {
        val image = CardLayer(kind = CardLayerKind.IMAGE, imageId = "image")
        val moved = transformCardLayer(image, 10_000f, -10_000f, 100f, 765f, 300f, 375f)
        assertEquals(1.0, moved.x, 0.0); assertEquals(0.0, moved.y, 0.0); assertEquals(2.0, moved.width, 0.0)
        assertEquals(45.0, moved.rotation, 0.0)
        val tiny = transformCardLayer(moved, 0f, 0f, .00001f, 0f, 300f, 375f)
        assertEquals(.01, tiny.width, 0.0)
    }
    @Test fun invalidInputNeverCorruptsTheLastGoodPlacement() {
        val layer = CardLayer(kind = CardLayerKind.TEXT, text = "🙂", width = .07)
        assertEquals(layer, transformCardLayer(layer, Float.NaN, 0f, 1f, 0f, 300f, 375f))
        assertEquals(layer, transformCardLayer(layer, 0f, 0f, 0f, 0f, 300f, 375f))
        assertEquals(layer, transformCardLayer(layer, 0f, 0f, 1f, 0f, 0f, 375f))
    }
}
