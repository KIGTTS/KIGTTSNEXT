package com.kigtts.next.ui.plugins

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState

/** A single Markdown row can be taller than the viewport. Align its end, not its start. */
internal suspend fun LazyListState.scrollToChatEnd(lastIndex: Int, animated: Boolean = false) {
    val last = layoutInfo.visibleItemsInfo.lastOrNull()
    if (last?.index != lastIndex) {
        if (animated) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    }
    val gap = chatBottomGap(layoutInfo)
    if (gap.isFinite() && gap > 0f) {
        if (animated) animateScrollBy(gap) else scrollBy(gap)
    }
}
