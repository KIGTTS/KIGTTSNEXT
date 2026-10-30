package com.kigtts.next.ui.identity

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kigtts.next.ui.components.NfcShareControl

@Composable internal fun ContactNfcControl(content: String) {
    val payload = remember(content) { runCatching {
        val uri = Uri.parse(content)
        val record = if (uri.scheme in setOf("https", "http", "mailto", "tel")) NdefRecord.createUri(uri)
            else NdefRecord.createTextRecord("zh", content)
        NdefMessage(arrayOf(record)).toByteArray()
    }.getOrNull() }
    NfcShareControl(payload)
}
