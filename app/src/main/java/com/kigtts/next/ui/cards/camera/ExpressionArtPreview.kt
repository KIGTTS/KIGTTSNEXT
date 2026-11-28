package com.kigtts.next.ui.cards.camera

import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
import com.kigtts.next.ui.cards.CardLayerHandles
import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.CardLayerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun ExpressionArtPreview(effect: ExpressionEffect, selected: Int, editable: Boolean,
    select: (Int) -> Unit, change: (Int, ExpressionLayer) -> Unit, remove: (Int) -> Unit) {
    val assets = LocalContext.current.assets
    val blurFace = effect.blurFaceEnabled
    val portrait by produceState<Pair<Boolean, Picture>?>(null, assets, blurFace) {
        value = withContext(Dispatchers.Default) {
            runCatching { blurFace to ExpressionPreviewPortrait.load(assets, blurFace) }.getOrNull()
        }
    }
    val artwork = remember(effect.layers) { effect.layers.map { it.svg to it.bitmapWebp } }
    val pictures by produceState<Pair<List<Pair<String, String?>>, List<Picture>>?>(null, artwork) {
        value = withContext(Dispatchers.Default) {
            runCatching { artwork to artwork.map { (svg, bitmap) -> renderExpressionArtwork(svg, bitmap) } }.getOrNull()
        }
    }
    val rendered = remember(effect, pictures) {
        pictures?.takeIf { it.first == artwork }?.second?.let { CompiledExpression(effect, it) }
    }
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val surface = colors.surfaceContainerLow
    val scrim = remember(surface) { Paint().apply { color = surface.copy(alpha = EXPRESSION_PORTRAIT_SCRIM).toArgb() } }
    val layers = rememberUpdatedState(effect.layers)
    val selectedLayer = effect.layers.getOrNull(selected)
    val selection = rememberUpdatedState(select)
    val update = rememberUpdatedState(change)
    Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(1.12f)
        .clip(MaterialTheme.shapes.large).background(surface), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(1f).clipToBounds()
            .testTag("expression-preview").then(if (editable) {
                Modifier.expressionLayerGestures(effect.id, layers, selection, update)
            } else Modifier)) {
            Canvas(Modifier.matchParentSize()) {
                val scale = size.width / EXPRESSION_PREVIEW_EDGE
                drawIntoCanvas {
                    val native = it.nativeCanvas
                    val save = native.save()
                    native.scale(scale, scale)
                    portrait?.takeIf { it.first == blurFace }?.let { (_, picture) ->
                        drawExpressionPortrait(native, picture, scrim)
                    }
                    rendered?.draw(native, listOf(expressionPreviewFace))
                    native.restoreToCount(save)
                }
                drawTrackingPoints(selectedLayer?.anchor, scale, accent)
            }
            if (editable && selectedLayer != null) {
                val placement = selectedLayer.placement(expressionPreviewFace)
                val center = placement.center
                val densityScale = maxWidth / EXPRESSION_PREVIEW_EDGE
                // Fit the handle frame inside the preview when the artwork reaches past an edge.
                val angle = Math.toRadians(placement.angle.toDouble())
                val extent = (abs(cos(angle)) + abs(sin(angle))).toFloat()
                val edge = maxOf(72.dp, densityScale * placement.edge).coerceAtMost((maxWidth - 2.dp) / extent)
                val margin = (edge * extent / 2f + 1.dp).coerceAtMost(maxWidth / 2f)
                val centerX = (densityScale * center.x).coerceIn(margin, maxWidth - margin)
                val centerY = (densityScale * center.y).coerceIn(margin, maxWidth - margin)
                Box(Modifier.align(Alignment.Center).offset {
                    val dx = (centerX - maxWidth / 2f).toPx()
                    val dy = (centerY - maxWidth / 2f).toPx()
                    IntOffset(dx.roundToInt(), dy.roundToInt())
                }.requiredSize(edge).graphicsLayer { rotationZ = placement.angle }
                    .border(1.dp, accent, RoundedCornerShape(4.dp))) {
                    val handle = CardLayer(id = "expression-$selected", kind = CardLayerKind.IMAGE,
                        width = selectedLayer.scale.toDouble(), rotation = selectedLayer.rotation.toDouble(),
                        flipX = selectedLayer.flipX)
                    CardLayerHandles(handle, { candidate ->
                        layers.value.getOrNull(selected)?.let { current ->
                            change(selected, current.transform(expressionPreviewFace, ExpressionPoint(0f, 0f),
                                candidate.width.toFloat() / current.scale, candidate.rotation.toFloat() - current.rotation)
                                .copy(flipX = candidate.flipX))
                        }
                    }, { remove(selected) }, canRemove = effect.layers.size > 1, widthRange = .15..3.0) {}
                }
            }
        }
    }
}

private fun DrawScope.drawTrackingPoints(activeAnchor: ExpressionAnchor?, scale: Float, accent: Color) {
    val sample = expressionPreviewFace
    val marked = when (activeAnchor) {
        ExpressionAnchor.EYES -> listOf(sample.leftEye, sample.rightEye)
        ExpressionAnchor.LEFT_EYE -> listOf(sample.leftEye)
        ExpressionAnchor.RIGHT_EYE -> listOf(sample.rightEye)
        ExpressionAnchor.NOSE -> listOf(sample.nose)
        ExpressionAnchor.MOUTH -> listOf(sample.mouthCenter)
        ExpressionAnchor.FOREHEAD -> listOf(ExpressionPoint(sample.eyeCenter.x, sample.eyeCenter.y - .65f * sample.eyeDistance))
        ExpressionAnchor.FACE -> {
            val face = sample.bounds
            drawRoundRect(accent, Offset(face.left, face.top) * scale,
                Size(face.width() * scale, face.height() * scale), CornerRadius(18.dp.toPx()),
                style = Stroke(2.dp.toPx()))
            emptyList()
        }
        null -> emptyList()
    }
    marked.forEach { point ->
        val at = Offset(point.x, point.y) * scale
        drawCircle(accent.copy(alpha = .22f), 11.dp.toPx(), at)
        drawCircle(accent, 8.dp.toPx(), at, style = Stroke(2.dp.toPx()))
        drawCircle(accent, 2.dp.toPx(), at)
    }
}
