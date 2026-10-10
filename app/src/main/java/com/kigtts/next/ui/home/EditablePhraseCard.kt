@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.ui.components.*

internal val QuickPhraseHeight = 88.dp

@Composable
fun EditablePhraseCard(phrase: QuickPhrase, edit: () -> Unit, delete: () -> Unit,
    neighbors: CornerNeighbors, modifier: Modifier = Modifier, grip: Modifier = Modifier, elevation: Dp = 0.dp,
    repository: com.kigtts.next.media.MediaRepository? = null) {
    Surface(onClick = feedbackClick(edit), modifier = modifier.heightIn(min = QuickPhraseHeight),
        color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = homeCardShape(neighbors), shadowElevation = elevation) {
        Box(propagateMinConstraints = true) {
        com.kigtts.next.ui.media.PhraseMediaBackdrop(phrase.mediaId, repository, Modifier.matchParentSize())
        Row(Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.DragIndicator, "拖动条目 " + phrase.text,
                Modifier.size(20.dp).testTag("phrase-grip-" + phrase.id).then(grip), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(if (phrase.mediaId == null) 1f else .65f)) {
                Text(phrase.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                if (phrase.description.isNotBlank()) Text(phrase.description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (phrase.mediaId != null) Spacer(Modifier.weight(.35f))
            ToolButton("删除 " + phrase.text, Icons.Default.Close, delete,
                Modifier.align(Alignment.Top).size(32.dp).testTag("delete-phrase-" + phrase.id),
                shapes = IconButtonDefaults.shapes(CircleShape, CircleShape))
        }
        }
    }
}
