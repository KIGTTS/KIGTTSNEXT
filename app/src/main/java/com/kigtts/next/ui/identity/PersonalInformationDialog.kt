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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable
internal fun PersonalInformationDialog(value: IdentityCard, stickers: StickerRepository, close: () -> Unit,
    save: (IdentityCard) -> Unit) {
    var name by remember(value.id) { mutableStateOf(value.nickname) }
    var bio by remember(value.id) { mutableStateOf(value.bio) }
    var contacts by remember(value.id) { mutableStateOf(value.contacts.joinToString("\n") { "${it.label}：${it.value}" }) }
    var avatar by remember(value.id) { mutableStateOf(value.avatar) }
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
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(feedbackClick { picker.launch("image/*") }, enabled = !working) {
                PersonalAvatar(avatar?.let(stickers::imageFile), Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Text("更换头像")
            }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("昵称") }, singleLine = true)
            OutlinedTextField(bio, { bio = it.take(240) }, label = { Text("简介") }, minLines = 2)
            OutlinedTextField(contacts, { contacts = it.take(4096) }, label = { Text("联系方式") },
                placeholder = { Text("微信：你的账号\n主页：https://…") }, minLines = 3)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(feedbackClick {
            val links = contacts.lineSequence().filter { it.isNotBlank() }.map { line ->
                val parts = line.split('：', ':', limit = 2)
                ContactLink(parts.first().trim().take(24), parts.getOrElse(1) { "" }.trim().take(512))
            }.toList()
            if (links.size > 8 || links.any { it.value.isBlank() }) error = "请按“名称：内容”填写，最多 8 项"
            else save(value.copy(nickname = name.trim(), bio = bio.trim(), avatar = avatar, contacts = links))
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
