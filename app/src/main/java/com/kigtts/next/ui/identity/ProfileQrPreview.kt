package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick

@Composable internal fun ProfileQrPreview(tile: ProfileTile, close: () -> Unit) {
    val context = LocalContext.current
    val qrLabel = uiText("profile.qr")
    val palette = nativeProfileQrPalette(tile.qrStyle, tile.qrKeepLight)
    MotionAlertDialog(close, title = { Text(tile.title.ifBlank { uiText("profile.qr_preview") }) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = Color(palette.paper)) {
                ProfileQrTile(tile.text, tile.qrStyle, uiText("accessibility.qr_preview"), Modifier.size(216.dp).padding(10.dp), tile.qrKeepLight)
            }
            SelectionContainer { Text(tile.text, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(qrLabel, tile.text)); close()
    }) { Text(uiText("profile.copy_contents")) } }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.close")) } })
}
