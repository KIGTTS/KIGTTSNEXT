package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExpressionArtPreview(effect: ExpressionEffect, activeAnchor: ExpressionAnchor? = null,
    modifier: Modifier = Modifier.fillMaxWidth().height(184.dp)) {
    val vectors = remember(effect.layers) { effect.layers.map { it.svg } }
    val pictures by produceState<Pair<List<String>, List<android.graphics.Picture>>?>(null, vectors) {
        value = withContext(Dispatchers.Default) {
            runCatching { vectors to vectors.map { com.kigtts.next.media.UiIcons.renderSvgPicture(it, 320) } }.getOrNull()
        }
    }
    val rendered = remember(effect.layers, pictures) {
        pictures?.takeIf { it.first == vectors }?.second?.let { CompiledExpression(effect, it) }
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        val scale = size.minDimension / 320f
        val x = size.width / 2f
        val y = size.height / 2f
        val sample = expressionPreviewFace
        drawIntoCanvas {
            val native = it.nativeCanvas
            val save = native.save()
            native.translate(x - 160f * scale, y - 160f * scale)
            native.scale(scale, scale)
            rendered?.draw(native, listOf(sample))
            native.restoreToCount(save)
        }
        val marked = when (activeAnchor) {
            ExpressionAnchor.EYES -> listOf(sample.leftEye, sample.rightEye)
            ExpressionAnchor.LEFT_EYE -> listOf(sample.leftEye)
            ExpressionAnchor.RIGHT_EYE -> listOf(sample.rightEye)
            ExpressionAnchor.NOSE -> listOf(sample.nose)
            ExpressionAnchor.MOUTH -> listOf(sample.leftMouth, sample.rightMouth)
            ExpressionAnchor.FOREHEAD -> listOf(ExpressionPoint(sample.eyeCenter.x, sample.eyeCenter.y - .65f * sample.eyeDistance))
            ExpressionAnchor.FACE -> {
                drawRoundRect(accent, androidx.compose.ui.geometry.Offset(x - 80f * scale, y - 124f * scale),
                    androidx.compose.ui.geometry.Size(160f * scale, 248f * scale), androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                emptyList()
            }
            null -> emptyList()
        }
        marked.forEach { point ->
            val at = androidx.compose.ui.geometry.Offset(x + (point.x - 160f) * scale, y + (point.y - 160f) * scale)
            drawCircle(accent.copy(alpha = .22f), 11.dp.toPx(), at)
            drawCircle(accent, 8.dp.toPx(), at, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(accent, 2.dp.toPx(), at)
        }
    }
}
