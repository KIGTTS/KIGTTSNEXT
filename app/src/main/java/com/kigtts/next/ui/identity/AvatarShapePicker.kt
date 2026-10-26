package com.kigtts.next.ui.identity

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.AvatarFrames
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun AvatarShapePicker(selected: String, select: (String) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().selectableGroup().testTag("avatar-shapes"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AvatarFrames.names.entries.toList(), key = { it.key }) { (id, label) ->
            val active = selected == id
            val colors = MaterialTheme.colorScheme
            val fill by animateColorAsState(if (active) colors.primaryContainer else colors.surfaceContainerLow, label = "avatar-shape-selection")
            val corner = RoundedCornerShape(20.dp)
            Column(Modifier.width(72.dp).clip(corner).background(fill)
                .border(1.dp, if (active) colors.primary else colors.outlineVariant, corner)
                .selectable(active, role = Role.RadioButton, onClick = feedbackClick { select(id) })
                .padding(vertical = 10.dp).testTag("avatar-shape/$id"), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val shape = remember(id) { AvatarFrames.shape(id) }
                Box(Modifier.size(38.dp).background(if (active) colors.primary else colors.secondary, shape))
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) colors.onPrimaryContainer else colors.onSurface)
            }
        }
    }
}
