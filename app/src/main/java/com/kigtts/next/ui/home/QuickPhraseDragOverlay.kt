@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.components.reorder.*

/** One stable card survives category changes without adding editor controls to quick sorting. */
@Composable
internal fun QuickPhraseDragOverlay(drag: ReorderDrag, phrases: List<QuickPhrase>, singleRow: Boolean,
    columns: Int, model: AppViewModel) {
    val position = rememberReorderDrop(drag)
    val density = LocalDensity.current
    val picked = phrases.firstOrNull { it.id == drag.key }
    val neighbors = remember(drag.key) {
        val group = phrases.filter { it.category == picked?.category }
        val index = group.indexOfFirst { it.id == picked?.id }
        if (singleRow) CornerNeighbors(start = index > 0, end = index < group.lastIndex)
        else CornerNeighbors(start = index % columns > 0, end = index % columns < columns - 1 && index < group.lastIndex,
            above = index >= columns, below = index + columns < group.size)
    }
    val elevation by animateDpAsState(if (picked != null && !drag.settling) 8.dp else 0.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "quick-phrase-lift")
    picked?.let { phrase ->
        PhraseTile(phrase, {}, neighbors, false, false, remember { MutableInteractionSource() },
            Modifier.size(with(density) { drag.floatingBounds.width.toDp() }, with(density) { drag.floatingBounds.height.toDp() })
                .graphicsLayer { translationX = position.value.x; translationY = position.value.y },
            model.mediaRepository, elevation = elevation)
    }
}
