package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SubtitleAlignment
import com.kigtts.next.ui.components.*

internal val SubtitleAlignment.boxAlignment: Alignment get() = when (this) {
    SubtitleAlignment.TOP_LEFT -> Alignment.TopStart
    SubtitleAlignment.TOP_CENTER -> Alignment.TopCenter
    SubtitleAlignment.TOP_RIGHT -> Alignment.TopEnd
    SubtitleAlignment.CENTER_LEFT -> Alignment.CenterStart
    SubtitleAlignment.CENTER -> Alignment.Center
    SubtitleAlignment.CENTER_RIGHT -> Alignment.CenterEnd
    SubtitleAlignment.BOTTOM_LEFT -> Alignment.BottomStart
    SubtitleAlignment.BOTTOM_CENTER -> Alignment.BottomCenter
    SubtitleAlignment.BOTTOM_RIGHT -> Alignment.BottomEnd
}
internal val SubtitleAlignment.textAlignment: TextAlign get() = when (column) {
    0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center
}

@Composable
internal fun SubtitleAlignmentDialog(selected: SubtitleAlignment, choose: (SubtitleAlignment) -> Unit, dismiss: () -> Unit) {
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(uiText("subtitle.align")) },
        text = {
            val finish = rememberDialogClose()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleAlignment.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { alignment ->
                        val active = alignment == selected
                        Surface(Modifier.weight(1f).testTag("subtitle-align-" + alignment.name.lowercase())
                            .selectable(active, onClick = feedbackClick { finish { choose(alignment); dismiss() } }, role = Role.RadioButton),
                            shape = MaterialTheme.shapes.large,
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AlignmentPreview(alignment)
                                Text(uiText("subtitle.alignment." + alignment.name.lowercase(java.util.Locale.ROOT)),
                                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                            }
                        }
                    } }
                }
            }
        }, confirmButton = { MotionTextButton(dialogFeedbackClick(dismiss)) { Text(uiText("action.cancel")) } })
}
