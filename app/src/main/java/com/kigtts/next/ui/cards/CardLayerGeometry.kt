package com.kigtts.next.ui.cards

import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.CardLayerKind

/** Keep coordinates independent of density, window size and a card's preview zoom. */
internal fun transformCardLayer(layer: CardLayer, panX: Float, panY: Float, zoom: Float, rotation: Float,
    canvasWidth: Float, canvasHeight: Float, allowOutside: Boolean = false): CardLayer {
    if (canvasWidth <= 0 || canvasHeight <= 0 || !panX.isFinite() || !panY.isFinite() ||
        !zoom.isFinite() || zoom <= 0 || !rotation.isFinite()) return layer
    val maximum = if (layer.kind == CardLayerKind.TEXT) .3 else 2.0
    val angle = ((layer.rotation + rotation + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    val minimum = if (allowOutside) -1.0 else 0.0; val maximumPosition = if (allowOutside) 2.0 else 1.0
    return layer.copy(x = (layer.x + panX / canvasWidth).coerceIn(minimum, maximumPosition),
        y = (layer.y + panY / canvasHeight).coerceIn(minimum, maximumPosition),
        width = (layer.width * zoom).coerceIn(.01, maximum), rotation = angle)
}
