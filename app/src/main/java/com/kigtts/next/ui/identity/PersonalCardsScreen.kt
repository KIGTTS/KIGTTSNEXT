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
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val uriHandler = LocalUriHandler.current
    com.kigtts.next.ui.navigation.RegisterPageActions(listOf(
        com.kigtts.next.ui.navigation.ToolbarAction("edit-personal-information", "编辑", Icons.Default.Edit) {
            model.navigate(Destination.PersonalInformationEditor)
        }))
    SettingsLayout {
        items.firstOrNull()?.let { card ->
            PersonalCardView(card, model.stickers, Modifier.fillMaxWidth().testTag("personal-card-${card.id}"))
            ProfileTags(card.tags)
            ProfileTileBoard(card.tiles, card.contacts, { profileImageFile(model.stickers, it) },
                openContact = { contact = it }, openLink = { url ->
                    safeProfileLink(url)?.let { runCatching { uriHandler.openUri(it) } }
                })
            SettingsGroup("我的名片") {
                SettingItem("分享名片", "", Icons.Default.Share, SettingPosition.SINGLE,
                    onClick = { model.faceShare.openSend(listOf(card.designId)) })
            }
        } ?: Text("用一张名片介绍自己", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        SettingsGroup("我的收藏") {
            SettingItem("扩列收藏夹", "", Icons.Default.Bookmarks, SettingPosition.SINGLE,
                onClick = { model.navigate(Destination.ContactFavorites) })
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
