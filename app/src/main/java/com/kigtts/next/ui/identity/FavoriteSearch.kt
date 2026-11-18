package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*
import kotlinx.coroutines.*
import org.json.JSONObject

internal data class IndexedFavorite(val item: SavedContact, val person: PublicIdentityProfile,
    val profile: JSONObject, val searchText: String)

/** Parse each bounded snapshot once on a worker, including the searchable text. */
internal fun indexFavorites(entries: List<SavedContact>): List<IndexedFavorite> = entries.mapNotNull { item ->
    runCatching {
        val json = JSONObject(item.profile)
        val person = PublicIdentityProfile.from(json)
        IndexedFavorite(item, person, json, (person.name + " " + person.bio + " " + person.tags.joinToString(" ")).lowercase())
    }.getOrNull()
}

internal fun searchFavorites(index: List<IndexedFavorite>, query: String): List<IndexedFavorite> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    return if (words.isEmpty()) index else index.filter { entry -> words.all { it in entry.searchText } }
}

@Composable internal fun FavoriteSearch(index: List<IndexedFavorite>, loading: Boolean, close: () -> Unit,
    open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results by produceState(index, index, query) {
        value = withContext(Dispatchers.Default) { searchFavorites(index, query) }
    }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = close)
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it.take(128) }, Modifier.weight(1f).focusRequester(focus).testTag("favorite-search-input"),
                        placeholder = { Text(uiText("profile.favorite_search")) }, singleLine = true, shape = CircleShape,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                            if (query.isNotEmpty()) IconButton(feedbackClick { query = "" }) { Icon(Icons.Default.Close, "清空") }
                        })
                    TextButton(feedbackClick(close)) { Text(uiText("action.cancel")) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("favorite-search-results"), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(results, key = { it.item.id }) { entry ->
                            SettingItem(entry.person.name, entry.person.tags.joinToString(" · ").ifBlank { entry.person.bio },
                                Icons.Default.ContactPage, SettingPosition.SINGLE, onClick = { open(entry.item.id) })
                        }
                    }
                    if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    EmptyContent(!loading && results.isEmpty(), Icons.Default.Search, "没有找到匹配的名片")
                }
            }
        }
    }
}
