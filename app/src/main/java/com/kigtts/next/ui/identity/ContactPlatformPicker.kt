package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*

@Composable internal fun ContactPlatformPicker(close: () -> Unit, title: String = "添加联系方式", choose: (SocialPlatform) -> Unit) {
    var query by remember { mutableStateOf("") }
    val groups = remember(query) { socialPlatformGroups(query) }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    MotionAlertDialog(close, title = { Text(title) }, text = {
        Column(Modifier.heightIn(max = 440.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(uiText("profile.platform_search")) }, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                if (groups.isEmpty()) item { Text(uiText("profile.platform_empty"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                groups.forEach { (group, platforms) ->
                    item(key = group) { Text(group.title, Modifier.padding(start = 12.dp, top = 20.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    items(platforms, key = { it.name }) { platform ->
                        TextButton(feedbackClick { choose(platform) }, Modifier.fillMaxWidth()) {
                            SocialContactIcon(ContactLink(platform.displayName, ""), Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp)); Text(platform.displayName, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}
