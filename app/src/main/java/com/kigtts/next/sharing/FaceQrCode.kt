package com.kigtts.next.sharing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
internal fun FaceQrCode(data: String, description: String, size: Dp = 240.dp) {
    var failed by remember(data) { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, data) {
        value = null
        try { value = withContext(Dispatchers.Default) {
            val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 600, 600, mapOf(EncodeHintType.MARGIN to 3, EncodeHintType.CHARACTER_SET to "UTF-8"))
            val pixels = IntArray(600 * 600) { index -> if (matrix[index % 600, index / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(pixels, 600, 600, Bitmap.Config.ARGB_8888).asImageBitmap()
        } } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val shown = image
        val shape = RoundedCornerShape(24.dp)
        // Round only the white frame; keep the square code and its quiet zone intact.
        Box(Modifier.sizeIn(maxWidth = size, maxHeight = size).aspectRatio(1f)
            .testTag("face-qr-frame").clip(shape).background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(12.dp),
            contentAlignment = Alignment.Center) {
            if (shown != null) Image(shown, description, Modifier.fillMaxSize())
            else if (failed) Text("二维码未能生成，请重试", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
            else CircularProgressIndicator()
        }
    }
}
