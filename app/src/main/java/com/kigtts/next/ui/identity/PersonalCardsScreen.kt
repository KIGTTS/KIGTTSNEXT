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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Composable
internal fun PersonalInformationEntry(model: AppViewModel) {
    val items by model.identities.items.collectAsStateWithLifecycle()
    val selected by model.identities.selectedId.collectAsStateWithLifecycle()
    val current = items.firstOrNull { it.id == selected } ?: items.firstOrNull()
    Surface(onClick = feedbackClick { model.navigate(Destination.PersonalCards) }, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().testTag("personal-information")) {
        if (current != null) PersonalCardView(current, model.stickers, Modifier.fillMaxWidth())
        else ListItem(headlineContent = { Text("个人信息与名片") }, supportingContent = { Text("设置头像、昵称和联系方式") },
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp,
        bottom = 16.dp + com.kigtts.next.ui.navigation.LocalPageBottomInset.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (items.isNotEmpty()) {
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth().testTag("personal-cards-pager"), key = { items[it].id }) { page ->
                PersonalCardView(items[page], model.stickers, Modifier.fillMaxWidth().testTag("personal-card-${items[page].id}"))
            }
            val card = items[pager.currentPage.coerceIn(items.indices)]
            LaunchedEffect(card.id) { model.identities.select(card.id) }
            Text("${pager.currentPage + 1} / ${items.size}", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.Gradient, null); Spacer(Modifier.width(12.dp))
                    Text("渐变遮罩", Modifier.weight(1f))
                    Switch(card.gradient, feedbackChange { checked -> scope.launch {
                        try { withContext(Dispatchers.IO) { model.identities.save(card.copy(gradient = checked)) } }
                        catch (error: Exception) { model.message(error.message ?: "设置未能保存") }
                    } }, Modifier.testTag("personal-card-gradient"))
                }
                Button(feedbackClick { editing = card }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("编辑个人信息") }
                FilledTonalButton(feedbackClick { model.navigate(Destination.CardEditor(card.designId)) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text("编辑背景与贴纸")
                }
                OutlinedButton(feedbackClick { model.faceShare.openSend(listOf(card.designId)) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("分享名片")
                }
                TextButton(feedbackClick { scope.launch {
                    try { withContext(Dispatchers.IO) { model.identities.remove(card.id) } }
                    catch (error: Exception) { model.message(error.message ?: "背景未能移除") }
                } }) { Text("移除此背景") }
            }
        } else Text("用一张名片介绍自己", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        FilledTonalButton(feedbackClick(::create), Modifier.padding(horizontal = 20.dp).fillMaxWidth(), enabled = !working) {
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
