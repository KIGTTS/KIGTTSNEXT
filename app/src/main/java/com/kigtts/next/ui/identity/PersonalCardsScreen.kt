package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.identity.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Composable
internal fun PersonalInformationEntry(model: AppViewModel) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val selected by model.identities.selectedId.collectAsStateWithLifecycle()
    val current = items.firstOrNull { it.id == selected } ?: items.firstOrNull()
    Surface(onClick = feedbackClick { model.navigate(Destination.PersonalCards) }, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().testTag("personal-information")) {
        if (current != null) {
            val pager = rememberPagerState(items.indexOfFirst { it.id == selected }.coerceAtLeast(0)) { items.size }
            LaunchedEffect(pager.settledPage, items) { items.getOrNull(pager.settledPage)?.let { model.identities.select(it.id) } }
            HorizontalPager(pager, Modifier.fillMaxWidth(), key = { items[it].id }) { page ->
                PersonalCardView(items[page], model.stickers, Modifier.fillMaxWidth())
            }
        }
        else ListItem(headlineContent = { Text("个人信息") }, supportingContent = { Text("设置头像、昵称和联系方式") },
            leadingContent = { Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer))
    }
}

@Composable
internal fun PersonalCardsScreen(model: AppViewModel) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == model.identities.selectedId.value }.coerceAtLeast(0)) { items.size }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<IdentityCard?>(null) }
    var working by remember { mutableStateOf(false) }
    fun create() {
        if (working) return
        working = true
        scope.launch {
            try {
                val card = withContext(Dispatchers.IO) { model.identities.create() }
                val index = snapshotFlow { items.indexOfFirst { it.id == card.id } }.first { it >= 0 }
                pager.animateScrollToPage(index)
            } catch (error: Exception) { model.message(error.message ?: "名片未能创建，请重试") }
            finally { working = false }
        }
    }
    SettingsLayout {
        if (items.isNotEmpty()) {
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth().testTag("personal-cards-pager"), key = { items[it].id }) { page ->
                PersonalCardView(items[page], model.stickers, Modifier.fillMaxWidth().testTag("personal-card-${items[page].id}"))
            }
            val card = items[pager.currentPage.coerceIn(items.indices)]
            LaunchedEffect(card.id) { model.identities.select(card.id) }
            SettingsGroup("个人资料") {
                SettingItem("头像与昵称", card.nickname.ifBlank { "设置头像、昵称和简介" }, Icons.Default.AccountCircle,
                    SettingPosition.FIRST, onClick = { editing = card })
                SettingItem("联系方式", if (card.contacts.isEmpty()) "添加联系方式" else "${card.contacts.size} 个入口", Icons.Default.ContactPage,
                    SettingPosition.LAST, onClick = { editing = card })
            }
            SettingsGroup("名片背景") {
                SettingItem("编辑背景与贴纸", "${pager.currentPage + 1} / ${items.size}", Icons.Default.Wallpaper,
                    SettingPosition.FIRST, onClick = { model.navigate(Destination.CardEditor(card.designId)) })
                SettingItem("分享名片", "", Icons.Default.Share, SettingPosition.MIDDLE,
                    onClick = { model.faceShare.openSend(listOf(card.designId)) })
                SettingItem("移除此背景", "", Icons.Default.DeleteOutline, SettingPosition.LAST, onClick = { scope.launch {
                    try { withContext(Dispatchers.IO) { model.identities.remove(card.id) } }
                    catch (error: Exception) { model.message(error.message ?: "背景未能移除") }
                } })
            }
        } else Text("用一张名片介绍自己", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        FilledTonalButton(feedbackClick(::create), Modifier.fillMaxWidth(), enabled = !working) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加名片背景")
        }
    }
    editing?.let { value -> PersonalInformationDialog(value, model.stickers, { editing = null }) { updated ->
        scope.launch {
            try { withContext(Dispatchers.IO) { model.identities.save(updated) }; editing = null }
            catch (error: Exception) { model.message(error.message ?: "名片未能保存") }
        }
    } }
}
