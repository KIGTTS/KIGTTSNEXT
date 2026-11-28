package com.kigtts.next.ui.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.kigtts.next.cards.CameraWatermarkStyle
import com.kigtts.next.cards.PhotoFrameGlass
import com.kigtts.next.cards.PolaroidPhotoRenderer
import com.kigtts.next.cards.WatermarkPalette
import com.kigtts.next.cards.WatermarkLayout
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Only the small edge textures are built off the UI thread; text-only changes reuse them. */
@Composable
internal fun rememberPhotoFrameGlass(image: ImageBitmap?, style: CameraWatermarkStyle?,
    geometry: PolaroidGeometry, card: PolaroidCard? = null): PhotoFrameGlass? {
    if (style?.palette != WatermarkPalette.GLASS || image == null) return null
    val sideEdges = style.edge > 0
    val frame = card?.frameStyleId?.substringAfter(".c-", "").orEmpty()
    val pose = card?.let { listOf(it.photoX, it.photoY, it.photoZoom, it.photoBackdrop) }
    val result by key(image) {
        produceState<PhotoFrameGlass?>(null, style.position, style.band, sideEdges, style.glassSource, style.layout, frame, pose) {
            // During a drag retain the last prepared strip until the new pose settles.
            if (value != null) delay(65)
            value = withContext(Dispatchers.Default) {
                val source = image.asAndroidBitmap()
                val factor = 256.0 / maxOf(geometry.photoWidth, geometry.photoHeight)
                val width = (geometry.photoWidth * factor).roundToInt().coerceAtLeast(1)
                val height = (geometry.photoHeight * factor).roundToInt().coerceAtLeast(1)
                val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(rendered)
                    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    if (card == null) canvas.drawBitmap(source, null, rect, paint)
                    else {
                        canvas.drawColor(android.graphics.Color.LTGRAY)
                        PolaroidPhotoRenderer.draw(canvas, source, rect, paint, card)
                    }
                    PhotoFrameGlass.prepare(rendered, Rect(0, 0, width, height), style.position, style.band, sideEdges,
                        style.glassSource, wholeBackdrop = style.layout == WatermarkLayout.CLASSIC)
                } finally { rendered.recycle() }
            }
        }
    }
    // Compose's render thread can still hold sampled images during a pass; let Bitmap GC
    // release old preview textures instead of recycling them while a previous layer may draw.
    return result
}
