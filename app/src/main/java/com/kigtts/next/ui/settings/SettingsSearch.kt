@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

internal data class SettingsSearchResult(val title: String, val path: String, val destination: Destination, val target: String = title)

internal fun searchSettings(query: String, cards: List<SettingsCard>): List<SettingsSearchResult> {
    val root = cards.flatMap { group -> group.links.map { link ->
        SettingsSearchResult(link.title, "设置 / ${group.title} / ${link.title}", link.destination)
    } }
    val personal = listOf(SettingsSearchResult("个人信息", "设置 / 个人信息", Destination.PersonalCards),
        SettingsSearchResult("头像与昵称", "设置 / 个人信息 / 头像与昵称", Destination.PersonalCards),
        SettingsSearchResult("联系方式", "设置 / 个人信息 / 联系方式", Destination.PersonalCards))
    val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return emptyList()
    return (root + nativeSettingsSearchEntries + personal).distinctBy { it.path }.filter { entry ->
        words.all { it in (entry.title + " " + entry.path).lowercase() }
    }.sortedWith(compareBy({ if (it.title.equals(query.trim(), true)) 0 else if (query.trim() in it.title) 1 else 2 }, { it.path.length }))
}

@Composable internal fun SettingsSearchDialog(cards: List<SettingsCard>, close: () -> Unit, open: (Destination) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, cards) { searchSettings(query, cards) }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = close)
        LaunchedEffect(Unit) { focus.requestFocus() }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it.take(128) }, Modifier.weight(1f).focusRequester(focus).testTag("settings-search-input"),
                        placeholder = { Text("搜索设置") }, singleLine = true, shape = androidx.compose.foundation.shape.CircleShape,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                            if (query.isNotEmpty()) IconButton(feedbackClick { query = "" }) { Icon(Icons.Default.Close, "清空") }
                        })
                    TextButton(feedbackClick(close)) { Text("取消") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(results, key = { it.path }) { result ->
                        SettingItem(result.title, result.path, Icons.Default.Settings, SettingPosition.SINGLE, onClick = {
                            SettingFocus.target = SettingFocus.Target(result.target); close(); open(result.destination)
                        })
                    }
                }
                EmptyContent(query.isNotBlank() && results.isEmpty(), Icons.Default.Search, "没有找到相关设置")
                }
            }
        }
    }
}
