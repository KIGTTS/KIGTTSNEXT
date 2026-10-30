package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Vertical scrolling collapses only the shortcuts; the shared tabs stay outside the pager. */
@Stable internal class CatalogHeaderScroll : NestedScrollConnection {
    var height by mutableFloatStateOf(0f)
        private set
    var collapsed by mutableFloatStateOf(0f)
        private set
    fun measureHeight(value: Float) {
        val pinned = height > 0f && collapsed >= height - .5f
        height = value
        collapsed = if (pinned) value else collapsed.coerceIn(0f, value)
    }
    private fun scroll(delta: Float): Offset {
        val old = collapsed
        collapsed = (old - delta).coerceIn(0f, height)
        return Offset(0f, old - collapsed)
    }
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) scroll(available.y) else Offset.Zero
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f) scroll(available.y) else Offset.Zero
}

@Composable internal fun CatalogCollapsingHeader(scroll: CatalogHeaderScroll, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        val hidden = scroll.collapsed.roundToInt().coerceAtMost(child.height)
        layout(child.width, child.height - hidden) { child.place(0, -hidden) }
    }) {
        Column(Modifier.fillMaxWidth().onSizeChanged { scroll.measureHeight(it.height.toFloat()) }
            .padding(top = 12.dp, bottom = 12.dp).testTag("catalog-shortcut-header"),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
