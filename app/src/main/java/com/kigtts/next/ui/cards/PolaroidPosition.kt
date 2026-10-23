package com.kigtts.next.ui.cards

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/**
 * Hold the high-priority mutation through the whole animation. A stopped touch stream may
 * subsequently send its old fling; cancelling only once before a Default animation is insufficient.
 * Uses PagerState's public custom-scroll contract without nesting another scroll mutation.
 */
internal suspend fun PagerState.positionPolaroid(page: Int) {
    scroll(MutatePriority.PreventUserInput) {
        val destination = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        updateTargetPage(destination)
        val pagePixels = layoutInfo.pageSize + layoutInfo.pageSpacing
        val pixels = (destination - currentPage - currentPageOffsetFraction) * pagePixels
        val duration = (260 + abs(destination - currentPage) * 35).coerceAtMost(480)
        var consumed = 0f
        animate(0f, pixels, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { value, _ ->
            consumed += scrollBy(value - consumed)
        }
        updateCurrentPage(destination, 0f)
    }
}
