package com.kigtts.next.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.first

@Composable
fun CategoryContentMotion(category: String, categories: List<String>, swipeEnabled: Boolean, select: (String) -> Unit,
    modifier: Modifier = Modifier, vertical: Boolean = false, content: @Composable (String, Boolean) -> Unit) {
    val latestCategories by rememberUpdatedState(categories)
    val latestCategory by rememberUpdatedState(category)
    val latestSelect by rememberUpdatedState(select)
    val latestContent by rememberUpdatedState(content)
    val sections = rememberSaveableStateHolder()
    val childScroll = remember { object : NestedScrollConnection {} }
    // Pixel scroll offsets belong to one axis. Reusing a vertical pager's offset after
    // the drawer grows into a grid can leave the first horizontal page half offscreen.
    val pager = key(vertical) {
        rememberPagerState(initialPage = categories.indexOf(category).coerceAtLeast(0)) { categories.size }
    }
    var userDragPending by remember(pager) { mutableStateOf(false) }
    // Tabs and drag-to-edge transfers drive the same pager. A finger drag commits only on settle,
    // so a recomposition cannot snap the page back to the previous selected category mid-gesture.
    LaunchedEffect(pager, category, categories) {
        val index = categories.indexOf(category)
        if (index >= 0 && pager.currentPage != index) {
            // The drawer can initially measure at zero height, before its opening animation.
            // Foundation cannot calculate an animated distance from an empty/zero-size layout.
            snapshotFlow { pager.layoutInfo.pageSize > 0 && pager.layoutInfo.visiblePagesInfo.isNotEmpty() }.first { it }
            if (swipeEnabled) pager.animateScrollToPage(index, animationSpec = tween(240))
            else pager.scrollToPage(index)
        }
    }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) userDragPending = true
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && userDragPending) {
                userDragPending = false
                latestCategories.getOrNull(pager.settledPage)?.takeIf { it != latestCategory }?.let(latestSelect)
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.layoutInfo.pageSize }.collect { size ->
            // A growing drawer must preserve the category, not a fractional offset from
            // its first tiny viewport. Request alignment for the next measure only.
            val selected = latestCategories.indexOf(latestCategory)
            if (size > 0 && selected >= 0 && !userDragPending)
                pager.requestScrollToPage(selected)
        }
    }
    val page: @Composable PagerScope.(Int) -> Unit = { index ->
        val target = categories[index]
        val active = target == latestCategory
        Box(Modifier.fillMaxSize().testTag("category-page-$target")) {
            Box(Modifier.fillMaxSize().then(if (active) Modifier else Modifier.clearAndSetSemantics {})) {
                sections.SaveableStateProvider(target) { latestContent(target, active) }
            }
        }
    }
    val area = modifier.fillMaxSize().clipToBounds().testTag("category-content-transition")
    key(vertical) {
        if (vertical) VerticalPager(pager, area, userScrollEnabled = swipeEnabled, pageNestedScrollConnection = childScroll,
            key = { categories[it] }, pageContent = page)
        else HorizontalPager(pager, area, userScrollEnabled = swipeEnabled, pageNestedScrollConnection = childScroll,
            key = { categories[it] }, pageContent = page)
    }
}
