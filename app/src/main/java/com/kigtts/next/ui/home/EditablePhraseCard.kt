@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.ui.components.*

internal val QuickPhraseHeight = com.kigtts.next.state.HomePanelSizes.ROW_HEIGHT.dp

@Composable
fun EditablePhraseCard(phrase: QuickPhrase, edit: () -> Unit, delete: () -> Unit,
    neighbors: CornerNeighbors, modifier: Modifier = Modifier, grip: Modifier = Modifier, elevation: Dp = 0.dp,
    repository: com.kigtts.next.media.MediaRepository? = null) {
    Surface(onClick = feedbackClick(edit), modifier = modifier.heightIn(min = QuickPhraseHeight),
        color = quickPhraseColor(phrase), contentColor = quickPhraseContentColor(phrase), shape = homeCardShape(neighbors), shadowElevation = elevation) {
        Box(propagateMinConstraints = true) {
        com.kigtts.next.ui.media.PhraseMediaBackdrop(phrase, repository, Modifier.matchParentSize(), quickPhraseColor(phrase))
        Row(Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.DragIndicator, uiText("accessibility.drag_shortcut", "name" to phrase.text),
                Modifier.size(20.dp).testTag("phrase-grip-" + phrase.id).then(grip), tint = LocalContentColor.current.copy(alpha = .76f))
            QuickPhraseText(phrase, Modifier.weight(if (phrase.mediaId == null) 1f else .65f))
            if (phrase.mediaId != null) Spacer(Modifier.weight(.35f))
            ToolButton(uiText("accessibility.remove_phrase", "phrase" to phrase.text), Icons.Default.Close, delete,
                Modifier.align(Alignment.Top).size(32.dp).testTag("delete-phrase-" + phrase.id),
                shapes = IconButtonDefaults.shapes(CircleShape, CircleShape))
        }
        }
    }
}
