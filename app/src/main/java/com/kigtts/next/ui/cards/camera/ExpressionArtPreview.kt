package com.kigtts.next.ui.cards.camera

import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExpressionArtPreview(effect: ExpressionEffect, activeAnchor: ExpressionAnchor? = null,
    modifier: Modifier = Modifier.fillMaxWidth().height(184.dp)) {
    val assets = LocalContext.current.assets
    val portrait by produceState<Picture?>(null, assets) {
        value = withContext(Dispatchers.Default) { runCatching { ExpressionPreviewPortrait.load(assets) }.getOrNull() }
    }
    val artwork = remember(effect.layers) { effect.layers.map { it.svg to it.bitmapWebp } }
    val pictures by produceState<Pair<List<Pair<String, String?>>, List<Picture>>?>(null, artwork) {
        value = withContext(Dispatchers.Default) {
            runCatching { artwork to artwork.map { (svg, bitmap) -> renderExpressionArtwork(svg, bitmap) } }.getOrNull()
        }
    }
    val rendered = remember(effect.layers, pictures) {
        pictures?.takeIf { it.first == artwork }?.second?.let { CompiledExpression(effect, it) }
    }
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val surface = colors.surfaceContainerLow
    val scrim = remember(surface) { Paint().apply { color = surface.copy(alpha = EXPRESSION_PORTRAIT_SCRIM).toArgb() } }
    Canvas(modifier.clip(MaterialTheme.shapes.large).background(surface)) {
        val scale = size.minDimension / EXPRESSION_PREVIEW_EDGE
        val origin = Offset((size.width - size.minDimension) / 2f, (size.height - size.minDimension) / 2f)
        val sample = expressionPreviewFace
        drawIntoCanvas {
            val native = it.nativeCanvas
            val save = native.save()
            native.translate(origin.x, origin.y)
            native.scale(scale, scale)
            portrait?.let { picture -> drawExpressionPortrait(native, picture, scrim) }
            rendered?.draw(native, listOf(sample))
            native.restoreToCount(save)
        }
        val marked = when (activeAnchor) {
            ExpressionAnchor.EYES -> listOf(sample.leftEye, sample.rightEye)
            ExpressionAnchor.LEFT_EYE -> listOf(sample.leftEye)
            ExpressionAnchor.RIGHT_EYE -> listOf(sample.rightEye)
            ExpressionAnchor.NOSE -> listOf(sample.nose)
            ExpressionAnchor.MOUTH -> listOf(sample.mouthCenter)
            ExpressionAnchor.FOREHEAD -> listOf(ExpressionPoint(sample.eyeCenter.x, sample.eyeCenter.y - .65f * sample.eyeDistance))
            ExpressionAnchor.FACE -> {
                val face = sample.bounds
                drawRoundRect(accent, origin + Offset(face.left, face.top) * scale,
                    Size(face.width() * scale, face.height() * scale), CornerRadius(18.dp.toPx()),
                    style = Stroke(2.dp.toPx()))
                emptyList()
            }
            null -> emptyList()
        }
        marked.forEach { point ->
            val at = origin + Offset(point.x, point.y) * scale
            drawCircle(accent.copy(alpha = .22f), 11.dp.toPx(), at)
            drawCircle(accent, 8.dp.toPx(), at, style = Stroke(2.dp.toPx()))
            drawCircle(accent, 2.dp.toPx(), at)
        }
    }
}
