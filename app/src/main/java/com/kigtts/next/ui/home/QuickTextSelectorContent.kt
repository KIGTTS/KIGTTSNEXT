package com.kigtts.next.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.media.PhraseMediaBackdrop
import com.kigtts.plugin.api.QuickTextSelectionMode
import kotlinx.coroutines.launch

/** Shared read-only content for dialogs and full-size selection surfaces. */
@Composable
internal fun QuickTextSelectorContent(phrases: List<QuickPhrase>, categories: List<String>,
    icons: Map<String, CategoryIcon>, repository: MediaRepository?, selected: Set<String>,
    mode: QuickTextSelectionMode, select: (QuickPhrase) -> Unit, modifier: Modifier = Modifier) {
    val groups = remember(phrases, categories) { (categories + phrases.map { it.category }).distinct() }
    if (groups.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("还没有快捷文本") }; return
    }
    key(groups) {
        val initial = phrases.firstOrNull { it.id in selected }?.category
        val pager = rememberPagerState(initialPage = groups.indexOf(initial).coerceAtLeast(0)) { groups.size }
        val tabs = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val grouped = remember(phrases) { phrases.groupBy { it.category } }
        LaunchedEffect(pager.currentPage) { tabs.animateScrollToItem(pager.currentPage) }
        Column(modifier) {
            LazyRow(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("selector-tabs"), state = tabs,
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(groups, key = { _, group -> group }) { index, group ->
                    val source = remember { MutableInteractionSource() }
                    ElasticTab(pager.currentPage == index, { scope.launch { pager.animateScrollToPage(index) } },
                        source, CornerNeighbors(start = index > 0, end = index < groups.lastIndex), false,
                        Modifier.testTag("selector-tab/$group"),
                        icon = { Icon((icons[group] ?: CategoryIcon.defaultFor(group)).vector(),
                            "分组图标：$group", Modifier.size(16.dp)) },
                        label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).testTag("selector-pages"), key = { groups[it] },
                beyondViewportPageCount = 1) { page ->
                val group = groups[page]
                val items = grouped[group].orEmpty()
                if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这个分组还没有快捷文本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.fillMaxSize().testTag("selector-group/$group"),
                    verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)) {
                    items(items, key = { it.id }) { phrase ->
                        SelectorCard(phrase, phrase.id in selected, mode, repository) { select(phrase) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorCard(phrase: QuickPhrase, selected: Boolean, mode: QuickTextSelectionMode,
    repository: MediaRepository?, onClick: () -> Unit) {
    Surface(onClick = feedbackClick(onClick), modifier = Modifier.height(QuickPhraseHeight)
        .testTag("selector-phrase/" + phrase.id).semantics {
            this.selected = selected
            role = if (mode == QuickTextSelectionMode.SINGLE) Role.RadioButton else Role.Checkbox
        }, shape = MaterialTheme.shapes.medium, color = quickPhraseColor(phrase), contentColor = quickPhraseContentColor(phrase),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Box(Modifier.fillMaxSize()) {
            PhraseMediaBackdrop(phrase, repository, Modifier.matchParentSize(), quickPhraseColor(phrase))
            QuickPhraseText(phrase, Modifier.fillMaxHeight().fillMaxWidth(if (phrase.mediaId == null) 1f else .60f)
                .padding(start = 12.dp, end = if (phrase.mediaId == null) 32.dp else 4.dp, top = 12.dp, bottom = 12.dp))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)) {
                if (mode == QuickTextSelectionMode.SINGLE) RadioButton(selected, null, Modifier.size(24.dp))
                else Checkbox(selected, null, Modifier.size(24.dp))
            }
        }
    }
}
