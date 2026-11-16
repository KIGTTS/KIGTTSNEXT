@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText

import com.kigtts.next.i18n.LocalUiLocale

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

internal data class SettingsSearchResult(val title: String, val path: String, val destination: Destination, val target: String = title, val alias: String = "")

internal fun searchSettings(query: String, cards: List<SettingsCard>, localize: (String) -> String = { it }): List<SettingsSearchResult> {
    fun label(key: String, fallback: String) = localize(key).takeUnless { it == key } ?: fallback
    val settings = label("nav.settings", "设置")
    val root = cards.flatMap { group -> group.links.map { link ->
        SettingsSearchResult(link.title, "$settings / ${group.title} / ${link.title}", link.destination)
    } }
    val personal = listOf(SettingsSearchResult("个人信息", "设置 / 个人信息", Destination.PersonalCards),
        SettingsSearchResult("头像与昵称", "设置 / 个人信息 / 编辑 / 头像与昵称", Destination.PersonalInformationEditor),
        SettingsSearchResult("联系方式", "设置 / 个人信息 / 编辑 / 联系方式", Destination.PersonalInformationEditor),
        SettingsSearchResult("背景与贴纸", "设置 / 个人信息 / 编辑 / 背景与贴纸", Destination.PersonalInformationEditor))
    val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return emptyList()
    return (root + nativeSettingsSearchEntries.map { entry ->
        entry.localized(localize)
    } + personal.map { it.localized(localize) }).distinctBy { it.path }.filter { entry ->
        words.all { it in (entry.title + " " + entry.path + " " + entry.alias).lowercase() }
    }.sortedWith(compareBy({ if (it.title.equals(query.trim(), true)) 0 else if (query.trim() in it.title) 1 else 2 }, { it.path.length }))
}

private fun SettingsSearchResult.localized(translate: (String) -> String): SettingsSearchResult {
    fun key(segment: String): String? = localizedSearchTitles[segment] ?: when (segment) {
        "设置" -> "nav.settings"
        "外观与交互" -> "settings.section.appearance"
        "个人信息" -> "nav.profile"
        "头像与昵称" -> "profile.name_avatar"
        "联系方式" -> "profile.contacts"
        "背景与贴纸" -> "profile.background_stickers"
        "编辑" -> "action.edit"
        "字体" -> "nav.fonts"
        else -> SettingsCategory.entries.firstOrNull { it.title == segment }
            ?.let { "settings.category.${it.name.lowercase()}" }
    }
    fun label(segment: String) = key(segment)?.let { key -> translate(key).takeUnless { it == key } } ?: segment
    val localizedTitle = label(title)
    val translatedPath = path.split(" / ").joinToString(" / ", transform = ::label)
    val focusTitle = if (title in localizedSearchTitles || title == "字体") localizedTitle else target
    return if (localizedTitle == title && translatedPath == path) this
    else copy(title = localizedTitle, path = translatedPath, target = focusTitle, alias = listOf(alias, title).filter(String::isNotEmpty).joinToString(" "))
}

@Composable internal fun SettingsSearchDialog(cards: List<SettingsCard>, close: () -> Unit, open: (Destination) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val language = LocalUiLocale.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val results = remember(query, cards, language, context) {
        searchSettings(query, cards) { key -> context.uiCopy(key, locale = language) }
    }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = close)
        LaunchedEffect(Unit) { focus.requestFocus() }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it.take(128) }, Modifier.weight(1f).focusRequester(focus).testTag("settings-search-input"),
                        placeholder = { Text(uiText("settings.search")) }, singleLine = true, shape = androidx.compose.foundation.shape.CircleShape,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                            if (query.isNotEmpty()) IconButton(feedbackClick { query = "" }) { Icon(Icons.Default.Close, uiText("action.clear")) }
                        })
                    TextButton(feedbackClick(close)) { Text(uiText("action.cancel")) }
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
                EmptyContent(query.isNotBlank() && results.isEmpty(), Icons.Default.Search, uiText("settings.search.empty"))
                }
            }
        }
    }
}
