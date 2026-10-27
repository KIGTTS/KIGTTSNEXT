@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.identity.ContactFavorites
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
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val repository by produceState<ContactFavorites?>(null) { value = withContext(Dispatchers.IO) { ContactFavorites.get(context) } }
    val saved by repository?.items?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val alreadySaved = repository?.let { store -> saved.any { it.id == store.key(profile) } } == true
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val stored = repository?.let { repo -> saved.firstOrNull { it.id == repo.key(profile) } }
            FaceProfileContent(stored?.let { JSONObject(it.profile) } ?: profile, image = { repository?.image(it) })
            FilledTonalButton({ if (!saving && !alreadySaved) scope.launch {
                saving = true
                try { withContext(Dispatchers.IO) { ContactFavorites.get(context).save(profile) } }
                catch (e: Exception) { error = e.message ?: "未能收藏，请重试" }
                finally { saving = false }
            } }, Modifier.fillMaxWidth()) { Text(if (alreadySaved) "已加入扩列收藏夹" else if (saving) "正在收藏…" else "加入扩列收藏夹") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FilledTonalButton(feedbackClick(close), Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}
