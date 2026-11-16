package com.kigtts.next.ui.identity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactFavorites
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.sharing.FaceProfileContent
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import com.kigtts.next.ui.settings.SettingsLayout
import kotlinx.coroutines.*

@Composable internal fun ContactFavoriteDetailScreen(model: AppViewModel, id: String) {
    val repository by produceState<ContactFavorites?>(null) { value = withContext(Dispatchers.IO) { model.contactFavorites } }
    val store = repository ?: return
    val items by store.items.collectAsState()
    val item = items.firstOrNull { it.id == id }
    val entry by produceState<IndexedFavorite?>(null, item) {
        value = withContext(Dispatchers.Default) { indexFavorites(listOfNotNull(item)).firstOrNull() }
    }
    var removing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val details = entry?.takeIf { item != null && it.item.id == id }
    RegisterPageActions(if (details == null) emptyList() else listOf(
        ToolbarAction("remove-contact-favorite", "移除收藏", Icons.Default.DeleteOutline) { removing = true }))
    SettingsLayout {
        if (details != null) Column(Modifier.fillMaxWidth().testTag("favorite-details/$id"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FavoriteCover(details)
            FaceProfileContent(details.profile, showHeader = details.item.cover == null, image = store::image)
        } else EmptyContent(item == null, Icons.Default.ContactPage, "这张名片已移除")
    }
    if (removing && details != null) MotionAlertDialog({ removing = false }, title = { Text("移除这张名片？") },
        confirmButton = { TextButton(dialogFeedbackClick { scope.launch {
            try {
                withContext(Dispatchers.IO) { store.remove(id) }
                if (model.state.value.destination == Destination.ContactFavoriteDetail(id)) model.navigateBack()
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { model.message(error.message ?: "未能移除名片") }
            finally { removing = false }
        } }) { Text("移除") } }, dismissButton = { TextButton(dialogFeedbackClick { removing = false }) { Text("取消") } })
}

@Composable private fun FavoriteCover(entry: IndexedFavorite) {
    val file = entry.item.cover ?: return
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val links = entry.person.contacts
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(MaterialTheme.shapes.extraLarge)) {
        ProfileTileImage(file, Modifier.matchParentSize(), "${entry.person.name}的名片")
        if (!entry.profile.optBoolean("coverButtonsBaked", true)) {
            PersonalContactButtons(links, true, { contact = it }, Modifier.padding(8.dp))
        } else if (links.isNotEmpty()) {
            // Older archives painted buttons into the cover. Keep the artwork and layer the hit targets over it.
            val height = maxWidth * (9f / 16f)
            val edge = height * (.085f * 32f / 22f)
            Row(Modifier.padding(start = height * .04f, top = height * .04f), horizontalArrangement = Arrangement.spacedBy(edge * .125f)) {
                val buttons = links.take(5) + links.drop(5).take(1)
                buttons.forEach { link ->
                    Box(Modifier.size(edge).clickable(role = Role.Button, onClick = feedbackClick { contact = link })
                        .semantics { contentDescription = "联系方式：${link.label.ifBlank { "链接" }}" })
                }
            }
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
