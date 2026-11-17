package com.kigtts.next.ui.sketch

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable internal fun SketchBackgroundDialog(state: SketchState, close: () -> Unit) {
    val copy = rememberUiCopy()
    MotionAlertDialog(onDismissRequest = close, title = { Text(copy("sketch.paper")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(16.dp)).testTag("sketch-paper-preview")) {
                drawSketchPaper(drawContext.canvas.nativeCanvas, Rect(0f, 0f, size.width, size.height),
                    state.background.toInt(), state.pattern, size.width, 1f)
            }
            ChoiceButtonGroup(copy("sketch.paper"), SketchPaperPattern.entries, state.pattern, { copy("sketch.pattern." + it.name.lowercase(java.util.Locale.ROOT)) }, state::setPaperPattern)
            Text(copy("sketch.paper_hint"))
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick(close)) { Text(copy("action.done")) } })
}
