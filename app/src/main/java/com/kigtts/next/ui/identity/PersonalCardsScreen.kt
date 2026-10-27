package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.identity.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*
import kotlinx.coroutines.*

@Composable
internal fun PersonalInformationEntry(model: AppViewModel) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val current = items.firstOrNull()
    Surface(onClick = feedbackClick { model.navigate(Destination.PersonalCards) }, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().testTag("personal-information")) {
        if (current != null) PersonalCardView(current, model.stickers, Modifier.fillMaxWidth())
        else ListItem(headlineContent = { Text("个人信息") }, supportingContent = { Text("设置头像、昵称和联系方式") },
            leadingContent = { Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer))
    }
}

@Composable
internal fun PersonalCardsScreen(model: AppViewModel) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<IdentityCard?>(null) }
    var working by remember { mutableStateOf(false) }
    var layoutEditing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<List<ProfileTile>>(emptyList()) }
    var tileEditor by remember { mutableStateOf<ProfileTile?>(null) }
    var addingTile by remember { mutableStateOf(false) }
    var tagsEditing by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val uriHandler = LocalUriHandler.current
    fun saveProfile(card: IdentityCard, done: () -> Unit) {
        if (working) return
        working = true
        scope.launch {
            try { withContext(Dispatchers.IO) { model.identities.save(card) }; done() }
            catch (error: Exception) { model.message(error.message ?: "未能保存，请重试") }
            finally { working = false }
        }
    }
    fun create() {
        if (working) return
        working = true
        scope.launch {
            try {
                editing = withContext(Dispatchers.IO) { model.identities.create() }
            } catch (error: Exception) { model.message(error.message ?: "名片未能创建，请重试") }
            finally { working = false }
        }
    }
    SettingsLayout {
        if (items.isNotEmpty()) {
            val card = items.first()
            PersonalCardView(card, model.stickers, Modifier.fillMaxWidth().testTag("personal-card-${card.id}"))
            ProfileTags(card.tags)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ tagsEditing = true }) { Icon(Icons.Default.Tag, null); Spacer(Modifier.width(6.dp)); Text("编辑标签") }
                if (!layoutEditing) FilledTonalButton({ draft = card.tiles; layoutEditing = true }) { Icon(Icons.Default.DashboardCustomize, null); Text("编辑磁贴") }
                else Row {
                    TextButton({ layoutEditing = false }) { Text("取消") }
                    FilledTonalButton({ saveProfile(card.copy(tiles = draft)) { layoutEditing = false } }) { Text(if (working) "保存中…" else "保存布局") }
                }
            }
            ProfileTileBoard(if (layoutEditing) draft else card.tiles, card.contacts, { profileImageFile(model.stickers, it) },
                editing = layoutEditing, change = { draft = it }, edit = { tileEditor = it }, remove = { tile -> draft = draft.filterNot { it.id == tile.id } },
                openContact = { contact = it }, openLink = { url -> safeProfileLink(url)?.let { runCatching { uriHandler.openUri(it) } } })
            if (layoutEditing) OutlinedButton({ if (draft.size < 24) addingTile = true else model.message("最多添加 24 个磁贴") }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Text("添加磁贴")
            }
            SettingsGroup("个人资料") {
                SettingItem("头像与昵称", card.nickname.ifBlank { "设置头像、昵称和简介" }, Icons.Default.AccountCircle,
                    SettingPosition.FIRST, onClick = { editing = card })
                SettingItem("联系方式", if (card.contacts.isEmpty()) "添加联系方式" else "${card.contacts.size} 个入口", Icons.Default.ContactPage,
                    SettingPosition.LAST, onClick = { editing = card })
            }
            SettingsGroup("我的名片") {
                SettingItem("背景与贴纸", "", Icons.Default.Wallpaper,
                    SettingPosition.FIRST, onClick = { model.navigate(Destination.CardEditor(card.designId)) })
                SettingItem("面对面分享", "", Icons.Default.Share, SettingPosition.MIDDLE,
                    onClick = { model.faceShare.openSend(listOf(card.designId)) })
                SettingItem("面对面接收", "", Icons.Default.MoveToInbox, SettingPosition.LAST,
                    onClick = { model.faceShare.openReceive() })
            }
        } else Text("用一张名片介绍自己", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        if (items.isEmpty()) FilledTonalButton(feedbackClick(::create), Modifier.fillMaxWidth(), enabled = !working) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("设置我的名片")
        }
        SettingsGroup("我的收藏") {
            SettingItem("扩列收藏夹", "", Icons.Default.Bookmarks, SettingPosition.SINGLE,
                onClick = { model.navigate(Destination.ContactFavorites) })
        }
    }
    val current = items.firstOrNull()
    if (current != null && (addingTile || tileEditor != null)) ProfileTileEditor(tileEditor, current.contacts, model.stickers,
        { addingTile = false; tileEditor = null }) { updated ->
        draft = if (draft.any { it.id == updated.id }) draft.map { if (it.id == updated.id) updated else it } else draft + updated
        addingTile = false; tileEditor = null
    }
    if (current != null && tagsEditing) ProfileTagsEditor(current.tags, { tagsEditing = false }) { tags ->
        saveProfile(current.copy(tags = tags)) { tagsEditing = false }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
    editing?.let { value -> PersonalInformationDialog(value, model.stickers, { editing = null }) { updated ->
        scope.launch {
            try { withContext(Dispatchers.IO) { model.identities.save(updated) }; editing = null }
            catch (error: Exception) { model.message(error.message ?: "名片未能保存") }
        }
    } }
}
