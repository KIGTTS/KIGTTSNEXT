package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import com.kigtts.next.ui.media.PickGalleryImage
import com.kigtts.next.ui.media.VisualMediaPicker
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
    save = { links -> JSONArray().apply { links.forEach { put(JSONObject().put("label", it.label).put("value", it.value).put("qrContent", it.qrContent)) } }.toString() },
    restore = { text -> JSONArray(text).let { list -> List(list.length()) { i -> list.getJSONObject(i).let { ContactLink(it.getString("label"), it.getString("value"), it.optString("qrContent").takeIf(String::isNotBlank)) } } } })

@Composable
internal fun PersonalInformationDialog(value: IdentityCard, stickers: StickerRepository, close: () -> Unit,
    save: (IdentityCard) -> Unit) {
    var name by rememberSaveable(value.id) { mutableStateOf(value.nickname) }
    var bio by rememberSaveable(value.id) { mutableStateOf(value.bio) }
    var contacts by rememberSaveable(value.id, stateSaver = ContactLinksSaver) { mutableStateOf(value.contacts) }
    var avatar by rememberSaveable(value.id) { mutableStateOf(value.avatar) }
    var avatarFrame by rememberSaveable(value.id) { mutableStateOf(value.avatarFrame) }
    var avatarSource by rememberSaveable(value.id) { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(VisualMediaPicker()) { uri ->
        if (uri != null) scope.launch {
            working = true
            try { avatarSource = withContext(Dispatchers.IO) { prepareAvatarSource(stickers.context, uri).path } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "头像未能打开，请换一张图片" }
            finally { working = false }
        }
    }
    fun clearSource() { avatarSource?.let { java.io.File(it).delete() }; avatarSource = null }
    val dismiss = { clearSource(); close() }
    avatarSource?.let { source -> AvatarCropDialog(java.io.File(source), stickers, { clearSource() }, { id -> avatar = id; clearSource() }) }
    MotionAlertDialog(dismiss, title = { Text(uiText("nav.profile")) }, icon = { Icon(Icons.Default.AccountCircle, null) },
        text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonalCardView(value.copy(nickname = name, bio = bio, avatar = avatar, contacts = contacts, avatarFrame = avatarFrame), stickers,
                Modifier.fillMaxWidth(), interactive = false)
            TextButton(feedbackClick { picker.launch(PickGalleryImage) }, enabled = !working) {
                PersonalAvatar(avatar?.let(stickers::imageFile), Modifier.size(48.dp), avatarFrame); Spacer(Modifier.width(12.dp)); Text(uiText("profile.avatar_replace"))
            }
            Text(uiText("profile.avatar_shape"), style = MaterialTheme.typography.titleSmall)
            AvatarShapePicker(avatarFrame) { avatarFrame = it }
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(uiText("profile.nickname")) }, singleLine = true)
            OutlinedTextField(bio, { bio = it.take(240) }, Modifier.fillMaxWidth(), label = { Text(uiText("profile.bio")) }, minLines = 2)
            ContactLinksEditor(contacts) { contacts = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(feedbackClick {
            save(value.copy(nickname = name.trim(), bio = bio.trim(), avatar = avatar, contacts = contacts, avatarFrame = avatarFrame))
        }, enabled = !working) { Text(uiText("action.save")) } }, dismissButton = { TextButton(dialogFeedbackClick(dismiss)) { Text(uiText("action.cancel")) } })
}
