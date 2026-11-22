package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText
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
import com.kigtts.next.identity.profileCoverAspect
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
    val removeFailed = uiText("profile.remove.failed")
    val details = entry?.takeIf { item != null && it.item.id == id }
    RegisterPageActions(if (details == null) emptyList() else listOf(
        ToolbarAction("remove-contact-favorite", uiText("profile.remove"), Icons.Default.DeleteOutline) { removing = true }))
    SettingsLayout {
        if (details != null) Column(Modifier.fillMaxWidth().testTag("favorite-details/$id"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FavoriteCover(details)
            FaceProfileContent(details.profile,
                showHeader = details.item.cover == null || details.profile.optBoolean("coverIsBackground"), image = store::image)
        } else EmptyContent(item == null, Icons.Default.ContactPage, uiText("profile.removed"))
    }
    if (removing && details != null) MotionAlertDialog({ removing = false }, title = { Text(uiText("profile.remove.ask")) },
        confirmButton = { TextButton(dialogFeedbackClick { scope.launch {
            try {
                withContext(Dispatchers.IO) { store.remove(id) }
                if (model.state.value.destination == Destination.ContactFavoriteDetail(id)) model.navigateBack()
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { model.message(error.message ?: removeFailed) }
            finally { removing = false }
        } }) { Text(uiText("action.remove")) } }, dismissButton = { TextButton(dialogFeedbackClick { removing = false }) { Text(uiText("action.cancel")) } })
}

@Composable private fun FavoriteCover(entry: IndexedFavorite) {
    val file = entry.item.cover ?: return
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val links = entry.person.contacts
    val aspect = profileCoverAspect(entry.profile)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(aspect).clip(MaterialTheme.shapes.extraLarge)) {
        ProfileTileImage(file, Modifier.matchParentSize(), uiText("accessibility.card_cover", "name" to entry.person.name))
        if (!entry.profile.optBoolean("coverButtonsBaked", true)) {
            PersonalContactButtons(links, true, { contact = it }, Modifier.padding(8.dp))
        } else if (links.isNotEmpty()) {
            // Older archives painted buttons into the cover. Keep the artwork and layer the hit targets over it.
            val height = maxWidth / aspect
            val edge = height * (.085f * 32f / 22f)
            Row(Modifier.padding(start = height * .04f, top = height * .04f), horizontalArrangement = Arrangement.spacedBy(edge * .125f)) {
                val buttons = links.take(5) + links.drop(5).take(1)
                val linkLabel = uiText("accessibility.link")
                buttons.forEach { link ->
                    val description = uiText("accessibility.contact_label", "name" to link.label.ifBlank { linkLabel })
                    Box(Modifier.size(edge).clickable(role = Role.Button, onClick = feedbackClick { contact = link })
                        .semantics { contentDescription = description })
                }
            }
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
