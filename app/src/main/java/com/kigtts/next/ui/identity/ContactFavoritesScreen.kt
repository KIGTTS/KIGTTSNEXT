package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactFavorites
import com.kigtts.next.identity.profileCoverAspect
import com.kigtts.next.sharing.FacePeerAvatar
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.*

@Composable internal fun ContactFavoritesScreen(model: AppViewModel) {
    val repository by produceState<ContactFavorites?>(null) { value = withContext(Dispatchers.IO) { model.contactFavorites } }
    val store = repository
    if (store == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val entries by store.items.collectAsState()
    val indexed by produceState(emptyList<IndexedFavorite>(), entries) {
        value = withContext(Dispatchers.Default) { indexFavorites(entries) }
    }
    var searching by remember { mutableStateOf(false) }
    val filtered = remember(indexed, entries) { val ids = entries.mapTo(hashSetOf()) { it.id }; indexed.filter { it.item.id in ids } }
    val list = rememberLazyListState()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model.externalFiles::profilePicked) }
    fun open(id: String) = model.navigate(Destination.ContactFavoriteDetail(id))
    RegisterPageActions(listOf(
        ToolbarAction("search-contact-favorites", uiText("profile.search_saved"), Icons.Default.Search) { searching = true },
        ToolbarAction("import-contact-favorite", uiText("profile.import"), Icons.Default.FileOpen) { importer.launch(arrayOf("*/*")) }))
    if (searching) FavoriteSearch(filtered, entries.isNotEmpty() && indexed.isEmpty(), { searching = false }) {
        searching = false; open(it)
    }
    ReadablePage(840.dp) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("contact-favorites-list"), state = list, contentPadding = pageContentPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (entries.isNotEmpty() && indexed.isEmpty()) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(filtered, key = { it.item.id }) { entry ->
                    val item = entry.item; val person = entry.person
                    Surface(onClick = feedbackClick { open(item.id) }, shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().testTag("favorite-card/${item.id}")) {
                        Column {
                            item.cover?.let { cover -> ProfileTileImage(cover, Modifier.fillMaxWidth().aspectRatio(profileCoverAspect(entry.profile)),
                                uiText("accessibility.card_cover", "name" to person.name)) }
                            if (item.cover == null || entry.profile.optBoolean("coverIsBackground")) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FacePeerAvatar(person.avatar, Modifier.size(56.dp), person.avatarFrame)
                                    Column(Modifier.weight(1f)) {
                                        Text(person.name, style = MaterialTheme.typography.titleMedium)
                                        Text(person.tags.joinToString(" · ").ifBlank { person.bio }, maxLines = 2,
                                            style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                        }
                    }
                }
            }
            EmptyContent(entries.isEmpty(), Icons.Default.ContactPage, uiText("profile.empty"),
                description = uiText("profile.empty.hint"))
        }
    }
}
