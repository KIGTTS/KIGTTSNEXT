package com.kigtts.next.ui.identity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

private val ContactLinksSaver = Saver<List<ContactLink>, String>(
    save = { links -> JSONArray().apply { links.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } }.toString() },
    restore = { text -> JSONArray(text).let { list -> List(list.length()) { i -> list.getJSONObject(i).let { ContactLink(it.getString("label"), it.getString("value")) } } } })

@Composable
internal fun PersonalInformationDialog(value: IdentityCard, stickers: StickerRepository, close: () -> Unit,
    save: (IdentityCard) -> Unit) {
    var name by rememberSaveable(value.id) { mutableStateOf(value.nickname) }
    var bio by rememberSaveable(value.id) { mutableStateOf(value.bio) }
    var contacts by rememberSaveable(value.id, stateSaver = ContactLinksSaver) { mutableStateOf(value.contacts) }
    var avatar by rememberSaveable(value.id) { mutableStateOf(value.avatar) }
    var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            working = true
            try { avatar = withContext(Dispatchers.IO) { stickers.importImage(uri).id } }
            catch (_: Exception) { error = "头像未能打开，请换一张图片" }
            finally { working = false }
        }
    }
    MotionAlertDialog(close, title = { Text("个人信息") }, icon = { Icon(Icons.Default.AccountCircle, null) },
        text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonalCardView(value.copy(nickname = name, bio = bio, avatar = avatar, contacts = contacts), stickers,
                Modifier.fillMaxWidth(), interactive = false)
            TextButton(feedbackClick { picker.launch("image/*") }, enabled = !working) {
                PersonalAvatar(avatar?.let(stickers::imageFile), Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Text("更换头像")
            }
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("昵称") }, singleLine = true)
            OutlinedTextField(bio, { bio = it.take(240) }, Modifier.fillMaxWidth(), label = { Text("简介") }, minLines = 2)
            ContactLinksEditor(contacts) { contacts = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(feedbackClick {
            save(value.copy(nickname = name.trim(), bio = bio.trim(), avatar = avatar, contacts = contacts))
        }, enabled = !working) { Text("保存") } }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text("取消") } })
}

@Composable
internal fun PersonalContactDialog(link: ContactLink, close: () -> Unit) {
    val context = LocalContext.current
    MotionAlertDialog(close, title = { Text(link.label) }, icon = { SocialContactIcon(link) }, text = { Text(link.value) },
        confirmButton = { TextButton(dialogFeedbackClick {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(link.label, link.value)); close()
        }) { Text("复制") } }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text("关闭") } })
}
