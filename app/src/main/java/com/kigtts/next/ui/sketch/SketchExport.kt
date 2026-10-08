package com.kigtts.next.ui.sketch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import java.io.File

/** Same stroke geometry and ordered eraser operations as the on-screen drawing layer. */
fun renderSketch(strokes: List<Stroke>, background: Int, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)
    val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    strokes.forEach { stroke ->
        paint.color = stroke.color.toInt()
        paint.alpha = (stroke.alpha * 255).toInt()
        paint.xfermode = if (stroke.erase) clear else null
        canvas.drawPath(strokePath(stroke, width.toFloat(), height.toFloat()), paint)
    }
    canvas.restoreToCount(layer)
    return bitmap
}

fun exportSketch(context: Context, strokes: List<Stroke>, background: Int, share: Boolean,
                 canvasSize: IntSize = IntSize(1080, 1600)): File {
    val scale = 1600f / maxOf(canvasSize.width, canvasSize.height).coerceAtLeast(1)
    val bitmap = renderSketch(strokes, background, (canvasSize.width * scale).toInt().coerceAtLeast(1),
        (canvasSize.height * scale).toInt().coerceAtLeast(1))
    val dir = File(context.filesDir, "sketches").apply { mkdirs() }
    val file = File(dir, "sketch-" + System.currentTimeMillis() + ".png")
    try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    finally { bitmap.recycle() }
    if (share) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享画板"))
    }
    return file
}
