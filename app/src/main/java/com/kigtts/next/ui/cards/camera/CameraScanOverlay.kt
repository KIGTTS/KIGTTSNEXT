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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun CameraScanOverlay(scan: CameraScanState, message: (String) -> Unit) {
    val copy = rememberCameraCopy()
    val text = scan.selected
    val dismiss = scan::clear
    val context = LocalContext.current
    val frame = scan.frame
    if (frame != null) CameraScanTargets(frame, dismiss, scan::choose)
    if (text == null && frame != null) return
    val sharingLink = text?.let(com.kigtts.next.sharing.CloudShareLinks::parse)
    if (sharingLink != null) {
        LaunchedEffect(sharingLink) {
            if (com.kigtts.next.sharing.CLOUD_SHARING_ENABLED) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sharingLink))
                    .setClassName(context.packageName, "com.kigtts.next.MainActivity")) }
            } else message(copy.text("camera.cloud_unavailable"))
            dismiss()
        }
        return
    }
    if (text != null && isQqScannerCode(text)) {
        LaunchedEffect(text) {
            if (openCameraCodeInQq(context, text)) dismiss()
            else {
                message(copy.text("camera.qq_unavailable"))
                dismiss()
            }
        }
        return
    }
    if (text == null) BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth * .62f, maxHeight * .60f, 260.dp)
        Canvas(Modifier.size(edge).testTag("camera-scan-frame")) {
            val inset = 2.dp.toPx()
            val corner = size.minDimension * .14f
            val radius = minOf(10.dp.toPx(), corner * .65f)
            val outline = Path().apply {
                for (dx in listOf(1f, -1f)) for (dy in listOf(1f, -1f)) {
                    val x = if (dx > 0f) inset else size.width - inset
                    val y = if (dy > 0f) inset else size.height - inset
                    moveTo(x + dx * corner, y)
                    lineTo(x + dx * radius, y)
                    quadraticTo(x, y, x, y + dy * radius)
                    lineTo(x, y + dy * corner)
                }
            }
            drawPath(outline, Color.Black.copy(alpha = .42f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawPath(outline, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(copy.text("camera.scan_guide"), color = Color.White, modifier = Modifier.offset(y = edge / 2 + 20.dp),
            style = MaterialTheme.typography.labelLarge)
    } else AlertDialog(dismiss, title = { Text(copy.text("camera.scan_result")) }, text = {
        androidx.compose.foundation.text.selection.SelectionContainer { Text(text) }
    }, confirmButton = {
        if (Uri.parse(text).scheme in setOf("https", "http")) TextButton(feedbackClick {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text))) }; dismiss()
        }) { Text(copy.text("camera.open_link")) }
        else TextButton(feedbackClick {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(copy.text("camera.scan_result"), text)); dismiss()
        }) { Text(copy.text("action.copy")) }
    }, dismissButton = { TextButton(feedbackClick(dismiss)) { Text(copy.text("camera.scan_again")) } })
}

private val qqCameraSchemes = setOf("mqq", "mqqapi", "mqqopensdkapi", "mqqwpa", "qqwallet", "qpay")
private val qqCameraHosts = setOf("qm.qq.com", "ti.qq.com", "qun.qq.com", "wpa.qq.com", "qianbao.qq.com",
    "pay.qq.com", "qpay.qq.com", "mqq.tenpay.com")
internal fun isQqScannerCode(raw: String): Boolean {
    val web = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return false
    val scheme = web.scheme?.lowercase() ?: return false
    if (scheme in qqCameraSchemes) return true
    val host = web.host?.lowercase() ?: return false
    return scheme in setOf("http", "https") && web.userInfo == null && qqCameraHosts.any { host == it || host.endsWith(".$it") }
}

private fun openCameraCodeInQq(context: Context, raw: String): Boolean {
    val qq = "com.tencent.mobileqq"
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw.trim())).setPackage(qq))
    }.isSuccess || runCatching {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(qq, "com.tencent.mobileqq.activity.SplashActivity"))
    }.isSuccess
}
