package com.kigtts.next.sharing

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable internal fun CloudShareLinkHost(model: AppViewModel) {
    val link by model.sharing.nfc.collectAsState()
    val url = link ?: return
    val payload = remember(url) { NdefMessage(arrayOf(NdefRecord.createUri(url))).toByteArray() }
    MotionAlertDialog(model.sharing::dismissNfc, title = { Text("碰一碰分享") }, text = {
        Column { FaceQrCode(url, "领取二维码"); NfcShareControl(payload) }
    }, confirmButton = { TextButton(dialogFeedbackClick(model.sharing::dismissNfc)) { Text("完成") } })
}
