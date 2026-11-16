package com.kigtts.next.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*

@Composable internal fun ProfileHeadingTile(tile: ProfileTile, editing: Boolean, modifier: Modifier = Modifier) {
    val inset = if (editing) 36.dp else 4.dp
    if (tile.kind == ProfileTileKind.TITLE) {
        Text(tile.title, modifier.padding(start = inset, top = 4.dp, end = 4.dp),
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Row(modifier.fillMaxSize().padding(start = inset, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileDividerStroke(tile.dividerStyle, Modifier.weight(1f))
            if (tile.title.isNotBlank()) {
                Text(tile.title, Modifier.weight(2f, fill = false), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProfileDividerStroke(tile.dividerStyle, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun ProfileDividerStroke(style: ProfileDividerStyle, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.outline
    Canvas(modifier.height(12.dp)) {
        val line = profileDividerPath(0f, size.width, size.height / 2, style == ProfileDividerStyle.WAVE, 2.5.dp.toPx(), 18.dp.toPx())
        drawPath(line.asComposePath(), ink, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
    }
}
