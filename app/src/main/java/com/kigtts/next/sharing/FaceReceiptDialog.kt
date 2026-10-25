package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.identity.PersonalContactDialog
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun rememberFaceReceipt(cardId: String): JSONObject? {
    val context = LocalContext.current.applicationContext
    return produceState<JSONObject?>(null, cardId) {
        value = withContext(Dispatchers.IO) { runCatching {
            context.getSharedPreferences("face-receipts", 0).getString(cardId, null)?.let(::JSONObject)
        }.getOrNull() }
    }.value
}

@Composable
internal fun FaceReceiptDialog(profile: JSONObject, close: () -> Unit) {
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val contacts = remember(profile) {
        val entries = profile.optJSONArray("contacts")
        (0 until minOf(entries?.length() ?: 0, 8)).mapNotNull { index ->
            entries?.optJSONObject(index)?.let { ContactLink(it.optString("label").take(24), it.optString("value").take(512)) }
                ?.takeIf { it.value.isNotBlank() }
        }
    }
    MotionAlertDialog(close, title = { Text(profile.optString("nickname", "KIGTTS 用户").take(40)) },
        icon = { FacePeerAvatar(profile.optString("avatar")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.optString("bio").takeIf { it.isNotBlank() }?.let { Text(it.take(240)) }
                contacts.forEach { link -> OutlinedButton(feedbackClick { contact = link }, Modifier.fillMaxWidth()) {
                    com.kigtts.next.ui.identity.SocialContactIcon(link); Spacer(Modifier.width(8.dp)); Text(link.label.ifBlank { "联系方式" })
                } }
            }
        }, confirmButton = { TextButton(dialogFeedbackClick(close)) { Text("完成") } })
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
