@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.artwork

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.ui.cards.camera.BurstPhotoCheck
import com.kigtts.next.ui.cards.camera.burstRangeSelection
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.launch

data class ArtworkChoice(val id: String, val title: String, val group: String = "")

/** Read-only choice component shared by native workflows and plugin requests. */
@Composable fun ArtworkSelectorPage(title: String, entries: List<ArtworkChoice>, multiple: Boolean,
    selectedIds: List<String> = emptyList(), close: () -> Unit, confirm: (List<String>) -> Unit,
    image: @Composable (ArtworkChoice, Modifier, Boolean) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(selectedIds.distinct().take(if (multiple) 64 else 1)) }
    var group by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = entries.map { it.group }.filter(String::isNotBlank).distinct()
    val visible = entries.filter { group.isBlank() || it.group == group }
    val validSelected = selected.filter { id -> entries.any { it.id == id } }
    val grid = rememberLazyGridState()
    fun toggle(id: String) { selected = if (id in selected) selected - id else if (multiple) (selected + id).take(64) else listOf(id) }
    Scaffold(Modifier.fillMaxSize().testTag("artwork-selector"), topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { ToolButton("返回", Icons.AutoMirrored.Filled.ArrowBack, close,
            colors = compactPlainIconColors()) }, actions = {
            if (multiple) ToolButton("全选或取消全选", Icons.Default.SelectAll, {
                selected = if (visible.all { it.id in selected }) selected - visible.map { it.id }.toSet()
                    else (selected + visible.map { it.id }).distinct().take(64)
            }, colors = compactPlainIconColors())
        })
    }, bottomBar = {
        Surface { Button(feedbackClick { confirm(entries.filter { it.id in selected }.map { it.id }) },
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), enabled = validSelected.isNotEmpty()) {
            Text(if (multiple) "选择 (${validSelected.size})" else "选择")
        } }
    }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        if (groups.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("") + groups).forEach { value -> FilterChip(group == value, feedbackClick { group = value }, label = { Text(value.ifBlank { "全部" }) }) }
        }
        if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有作品") }
        LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.weight(1f).fillMaxWidth()
            .burstRangeSelection(grid, multiple && preview == null, visible.indices.filter { visible[it].id in selected }) { indices ->
                val hidden = selected.filter { id -> visible.none { it.id == id } }
                selected = (hidden + indices.mapNotNull { visible.getOrNull(it)?.id }).distinct().take(64)
            }, state = grid, contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { entry -> Surface(onClick = feedbackClick { preview = entry.id },
                shape = MaterialTheme.shapes.large, modifier = Modifier.aspectRatio(1f).testTag("artwork/" + entry.id)) {
                Box(contentAlignment = Alignment.Center) {
                    image(entry, Modifier.fillMaxSize(), false)
                    BurstPhotoCheck(entry.id in selected, true, { toggle(entry.id) }, Modifier.align(Alignment.TopEnd))
                }
            } }
        }
    } }
    preview?.let { id ->
        val pager = rememberPagerState(visible.indexOfFirst { it.id == id }.coerceAtLeast(0)) { visible.size }
        val scope = rememberCoroutineScope()
        Dialog({ preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .96f)) {
                Box {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { index -> image(visible[index], Modifier.fillMaxSize().padding(vertical = 64.dp), true) }
                    IconButton(feedbackClick { preview = null }, Modifier.align(Alignment.TopStart).statusBarsPadding()) { Icon(Icons.Default.Close, "关闭预览", tint = androidx.compose.ui.graphics.Color.White) }
                    visible.getOrNull(pager.currentPage)?.let { current -> BurstPhotoCheck(current.id in selected, true, { toggle(current.id) }, Modifier.align(Alignment.TopEnd).statusBarsPadding()) }
                    if (pager.currentPage > 0) IconButton(feedbackClick { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, Modifier.align(Alignment.CenterStart)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一张", tint = androidx.compose.ui.graphics.Color.White) }
                    if (pager.currentPage < visible.lastIndex) IconButton(feedbackClick { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.align(Alignment.CenterEnd)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一张", tint = androidx.compose.ui.graphics.Color.White) }
                }
            }
        }
    }
}

@Composable fun ArtworkSelectorDialog(title: String, entries: List<ArtworkChoice>, multiple: Boolean,
    selectedIds: List<String> = emptyList(), close: () -> Unit, confirm: (List<String>) -> Unit,
    image: @Composable (ArtworkChoice, Modifier, Boolean) -> Unit) {
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ArtworkSelectorPage(title, entries, multiple, selectedIds, close, confirm, image)
    }
}
