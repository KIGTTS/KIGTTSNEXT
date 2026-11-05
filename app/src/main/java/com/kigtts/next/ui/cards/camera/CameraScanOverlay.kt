package com.kigtts.next.ui.cards.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun CameraScanOverlay(text: String?, dismiss: () -> Unit) {
    val context = LocalContext.current
    val sharingLink = text?.let(com.kigtts.next.sharing.CloudShareLinks::parse)
    if (sharingLink != null) {
        LaunchedEffect(sharingLink) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sharingLink))
                .setClassName(context.packageName, "com.kigtts.next.MainActivity")) }
            dismiss()
        }
        return
    }
    if (text == null) BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth * .62f, maxHeight * .60f, 260.dp)
        Canvas(Modifier.size(edge).testTag("camera-scan-frame")) {
            val inset = 2.dp.toPx()
            val corner = size.minDimension * .14f
            for (x in listOf(inset, size.width - inset)) for (y in listOf(inset, size.height - inset)) {
                val start = Offset(x, y)
                val ends = listOf(Offset(x + if (x == inset) corner else -corner, y),
                    Offset(x, y + if (y == inset) corner else -corner))
                for (end in ends) {
                    drawLine(Color.Black.copy(alpha = .42f), start, end, 4.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, start, end, 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Text("将二维码或条形码放入框内", color = Color.White, modifier = Modifier.offset(y = edge / 2 + 20.dp),
            style = MaterialTheme.typography.labelLarge)
    } else AlertDialog(dismiss, title = { Text("识别结果") }, text = {
        androidx.compose.foundation.text.selection.SelectionContainer { Text(text) }
    }, confirmButton = {
        if (Uri.parse(text).scheme in setOf("https", "http")) TextButton(feedbackClick {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text))) }; dismiss()
        }) { Text("打开链接") }
        else TextButton(feedbackClick {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("识别结果", text)); dismiss()
        }) { Text("复制") }
    }, dismissButton = { TextButton(feedbackClick(dismiss)) { Text("继续扫码") } })
}
