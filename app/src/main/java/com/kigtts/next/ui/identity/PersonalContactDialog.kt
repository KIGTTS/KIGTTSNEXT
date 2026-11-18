package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import android.content.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.sharing.FaceQrCode

@Composable internal fun PersonalContactDialog(link: ContactLink, close: () -> Unit) {
    val context = LocalContext.current; val payload = contactPayload(link)
    MotionAlertDialog(close, title = { Text(link.label) }, icon = { SocialContactIcon(link) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FaceQrCode(payload, uiText("profile.contact_qr", "name" to link.label))
            Text(link.value, style = MaterialTheme.typography.bodyMedium)
            if (com.kigtts.next.sharing.CLOUD_SHARING_ENABLED) ContactNfcControl(payload)
        }
    }, confirmButton = { TextButton(dialogFeedbackClick {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(link.label, link.value)); close()
    }) { Text(uiText("action.copy")) } }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.close")) } })
}
