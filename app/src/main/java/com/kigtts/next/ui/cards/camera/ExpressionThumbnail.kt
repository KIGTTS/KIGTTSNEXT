package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kigtts.next.camera.expressions.CompiledExpression
import com.kigtts.next.camera.expressions.ExpressionEffect
import com.kigtts.next.camera.expressions.ExpressionFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val expressionPreviewFace = ExpressionFace.SAMPLE.copy(surface = null)
private val thumbnailWorker = Dispatchers.Default.limitedParallelism(1)
private val thumbnails = LruCache<ExpressionEffect, ImageBitmap>(48)

@Composable
internal fun expressionThumbnail(effect: ExpressionEffect): ImageBitmap? {
    val cached = remember(effect) { thumbnails.get(effect) }
    return produceState(cached, effect) {
        value = cached ?: withContext(thumbnailWorker) {
            thumbnails.get(effect) ?: runCatching {
                val picture = CompiledExpression.compile(effect)
                val image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image)
                canvas.scale(.5f, .5f)
                picture.draw(canvas, listOf(expressionPreviewFace))
                image.asImageBitmap().also { thumbnails.put(effect, it) }
            }.getOrNull()
        }
    }.value
}
