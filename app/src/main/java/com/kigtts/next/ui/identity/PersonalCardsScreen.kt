package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.rememberUiCopy
import com.kigtts.next.i18n.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.identity.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun PersonalInformationEntry(model: AppViewModel) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val current = items.firstOrNull()
    val open = { model.navigate(Destination.PersonalCards) }
    val entry = Modifier.fillMaxWidth().testTag("personal-information")
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
        modifier = if (current == null) entry.clickable(onClick = feedbackClick(open)) else Modifier.fillMaxWidth()) {
        if (current != null) PersonalCardView(current, model.stickers, entry, shape = RectangleShape, onOpen = open,
            onLongClick = { model.sharing.shareMedia(listOf(current.designId)) })
        else ListItem(headlineContent = { Text(uiText("nav.profile")) }, supportingContent = { Text(uiText("profile.setup_hint")) },
            leadingContent = { Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer))
    }
}

@Composable
internal fun PersonalCardsScreen(model: AppViewModel, initiallyEditing: Boolean = false) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val card = items.firstOrNull()
    val copy = rememberUiCopy()
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    val draftSaver = remember(card?.id) { Saver<IdentityCard?, String>(
        save = { it?.let { value -> PersonalProfile.from(value).json().toString() } },
        restore = { text -> card?.let { PersonalProfile.from(JSONObject(text)).on(it) } }) }
    var draft by rememberSaveable(card?.id, stateSaver = draftSaver) { mutableStateOf(card) }
    var information by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(false) }
    var addingTile by remember { mutableStateOf(false) }
    var tileEditor by remember { mutableStateOf<ProfileTile?>(null) }
    var working by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(card, editing) { if (!editing) draft = card }
    fun persist(done: () -> Unit) {
        val value = draft ?: return
        if (working) return
        working = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    model.identities.save(PersonalProfile.from(value).on(model.identities.current ?: value))
                }
                done()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.message(copy("profile.save_failed")) }
            finally { working = false }
        }
    }
    fun finish() { persist { editing = false } }
    fun begin() {
        if (card != null) { draft = card; editing = true; return }
        if (working) return
        working = true
        scope.launch {
            try { draft = withContext(Dispatchers.IO) { model.identities.create() }; editing = true; information = true }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.message(copy("profile.create_failed")) }
            finally { working = false }
        }
    }
    RegisterPageActions(listOfNotNull(
        card?.takeIf { !editing }?.let { ToolbarAction("share-personal-information", uiText("profile.share"), Icons.Default.Share) {
            model.sharing.shareMedia(listOf(it.designId))
        } },
        ToolbarAction("edit-personal-information", if (editing) uiText("action.done") else uiText("action.edit"),
            if (editing) Icons.Default.Check else Icons.Default.Edit) { if (editing) finish() else begin() }))
    RegisterPageLoading(working)
    RegisterPageBackGuard(editing && draft != null, ::finish)
    BackHandler(editing && draft != null, ::finish)
    SettingsLayout {
        (if (editing) draft else card)?.let { displayed ->
            PersonalCardView(displayed, model.stickers, Modifier.fillMaxWidth().testTag("personal-card-${displayed.id}"),
                editing = editing, editInformation = { information = true }, editBackground = {
                    persist { model.navigate(Destination.CardEditor(displayed.designId)) }
                }, onLongClick = if (!editing) ({ model.sharing.shareMedia(listOf(displayed.designId)) }) else null)
            ProfileEditableTags(displayed.tags, editing, { draft = displayed.copy(tags = it) }, { tags = true })
            ProfileTileBoard(displayed.tiles, displayed.contacts, { profileImageFile(model.stickers, it) },
                editing = editing, change = { draft = displayed.copy(tiles = it) }, edit = { tileEditor = it },
                remove = { removed -> draft = displayed.copy(tiles = displayed.tiles.filterNot { it.id == removed.id }) },
                openContact = { contact = it }, openLink = { url ->
                    safeProfileLink(url)?.let { runCatching { uriHandler.openUri(it) } }
                })
            AnimatedVisibility(editing, enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(220))) {
                OutlinedButton(feedbackClick { if (displayed.tiles.size < 24) addingTile = true else model.message(copy("profile.tile_limit")) },
                    Modifier.fillMaxWidth().testTag("profile-add-tile")) { Icon(Icons.Default.Add, null); Text(uiText("profile.add_tile")) }
            }
        } ?: FilledTonalButton(feedbackClick(::begin), Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Text(uiText("profile.create"))
        }
    }
    draft?.let { value ->
        if (information) PersonalInformationDialog(value, model.stickers, { information = false }) { updated -> draft = updated; information = false }
        if (tags) ProfileTagsEditor(value.tags, { tags = false }) { draft = value.copy(tags = it); tags = false }
        if (addingTile || tileEditor != null) ProfileTileEditor(tileEditor, value.contacts, model.stickers,
            { addingTile = false; tileEditor = null }) { updated ->
            draft = value.copy(tiles = if (value.tiles.any { it.id == updated.id }) value.tiles.map { if (it.id == updated.id) updated else it } else value.tiles + updated)
            addingTile = false; tileEditor = null
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
