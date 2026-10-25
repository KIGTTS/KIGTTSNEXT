package com.kigtts.next.sharing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FaceQrCode(data: String, description: String) {
    val image by produceState<ImageBitmap?>(null, data) {
        value = withContext(Dispatchers.Default) {
            val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 600, 600, mapOf(EncodeHintType.MARGIN to 3, EncodeHintType.CHARACTER_SET to "UTF-8"))
            val pixels = IntArray(600 * 600) { index -> if (matrix[index % 600, index / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(pixels, 600, 600, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) Image(shown, description, Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).aspectRatio(1f).background(Color.White))
        else CircularProgressIndicator()
    }
}
