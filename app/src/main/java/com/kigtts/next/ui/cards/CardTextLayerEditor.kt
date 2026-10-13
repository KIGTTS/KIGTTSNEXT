package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.CardLayer

/** Text stays in the same tools sheet as the selected sticker. */
@Composable
internal fun CardTextLayerEditor(layer: CardLayer, enabled: Boolean, change: (CardLayer) -> Unit, finish: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(layer.text, { if (it.length <= 2048) change(layer.copy(text = it)) },
            Modifier.fillMaxWidth().testTag("card-layer-text"), label = { Text("文字内容") },
            enabled = enabled, minLines = 2, maxLines = 5)
        MotionTextButton(feedbackClick(finish), enabled = enabled) { Text("完成文字编辑") }
    }
}
