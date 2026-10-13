package com.kigtts.next.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

/** One export choice for still and live photographs; the resulting quick item is always media. */
@Composable
internal fun CardQuickExportDialog(actions: CardWorkspaceActions) {
    val card = actions.quickCard ?: return
    var frame by rememberSaveable(card.id) { mutableStateOf(true) }
    var stickers by rememberSaveable(card.id) { mutableStateOf(true) }
    MotionAlertDialog(onDismissRequest = actions::cancelQuick, dismissEnabled = !actions.busy,
        title = { Text("添加到快捷文本") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardExportOption("保留相框", frame, !actions.busy, "quick-card-frame") { frame = it }
                CardExportOption("保留贴纸", stickers, !actions.busy, "quick-card-stickers") { stickers = it }
            }
        }, confirmButton = {
            val finish = rememberDialogClose()
            MotionTextButton(feedbackClick { actions.publishQuick(frame, stickers, finish) },
                Modifier.testTag("quick-card-create"), enabled = !actions.busy) { Text("添加") }
        }, dismissButton = {
            MotionTextButton(dialogFeedbackClick(actions::cancelQuick), enabled = !actions.busy) { Text("取消") }
        })
}

@Composable
private fun CardExportOption(label: String, selected: Boolean, enabled: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = feedbackClick { change(!selected) })
        .padding(vertical = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(selected, feedbackChange(change), enabled = enabled)
    }
}
