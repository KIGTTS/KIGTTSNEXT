package com.kigtts.next.ui.identity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.sharing.FacePeerAvatar
import com.kigtts.next.sharing.FaceProfileContent
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.pageContentPadding
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun ContactFavoritesScreen(model: AppViewModel) {
    val repository by produceState<ContactFavorites?>(null) { value = withContext(Dispatchers.IO) { model.contactFavorites } }
    val store = repository
    if (store == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val entries by store.items.collectAsState()
    val indexed by produceState(emptyList<Pair<SavedContact, PublicIdentityProfile>>(), entries) {
        value = withContext(Dispatchers.Default) { entries.map { it to PublicIdentityProfile.from(JSONObject(it.profile)) } }
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<SavedContact?>(null) }
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val filtered = remember(indexed, entries, search) { indexed.filter { (item, p) -> entries.any { it.id == item.id } && (search.isBlank() ||
        (p.name + " " + p.bio + " " + p.tags.joinToString(" ")).contains(search.trim(), true)) } }
    ReadablePage(840.dp) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            label = { Text("搜索昵称或标签") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
    Box(Modifier.weight(1f).fillMaxWidth()) {
    LazyColumn(Modifier.fillMaxSize().testTag("contact-favorites-list"), contentPadding = pageContentPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (entries.isNotEmpty() && indexed.isEmpty()) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(filtered, key = { it.first.id }) { (item, person) ->
            val profile = remember(item.profile) { JSONObject(item.profile) }
            val open = expanded == item.id
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().testTag("favorite-card/${item.id}")) {
                Column {
                    Surface(onClick = feedbackClick { expanded = if (open) null else item.id }, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column {
                        item.cover?.let { ProfileTileImage(it, Modifier.fillMaxWidth().aspectRatio(16f / 9), "${person.name}的名片") }
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FacePeerAvatar(person.avatar, Modifier.size(56.dp), person.avatarFrame)
                            Column(Modifier.weight(1f)) {
                                Text(person.name, style = MaterialTheme.typography.titleMedium)
                                Text(person.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: person.bio, maxLines = 2,
                                    style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "收起名片" else "展开名片")
                        }
                        }
                    }
                    AnimatedVisibility(open, enter = expandVertically(tween(240)) + fadeIn(tween(180)), exit = shrinkVertically(tween(220)) + fadeOut(tween(150))) {
                        Column(Modifier.padding(16.dp).testTag("favorite-details/${item.id}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            FaceProfileContent(profile, image = store::image)
                            TextButton(feedbackClick { removing = item }, Modifier.align(Alignment.End)) { Icon(Icons.Default.DeleteOutline, null); Text("移除收藏") }
                        }
                    }
                }
            }
        }
    }
    EmptyContent(entries.isEmpty() || indexed.isNotEmpty() && filtered.isEmpty(),
        if (search.isBlank()) Icons.Default.ContactPage else Icons.Default.Search,
        if (entries.isEmpty()) "还没有收藏的名片" else "没有找到匹配的名片",
        description = if (entries.isEmpty()) "面对面收到或收藏的名片会保存在这里" else null)
    }
    }
    }
    removing?.let { item -> MotionAlertDialog({ removing = null }, title = { Text("移除这张名片？") },
        confirmButton = { TextButton(feedbackClick { scope.launch {
            try { withContext(Dispatchers.IO) { store.remove(item.id) }; removing = null }
            catch (e: Exception) { model.message(e.message ?: "未能移除名片") }
        } }) { Text("移除") } }, dismissButton = { TextButton(dialogFeedbackClick { removing = null }) { Text("取消") } }) }
}
