package com.kigtts.next.ui.sketch

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
    MotionAlertDialog(onDismissRequest = close, title = { Text("画布背景") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(16.dp)).testTag("sketch-paper-preview")) {
                drawSketchPaper(drawContext.canvas.nativeCanvas, Rect(0f, 0f, size.width, size.height),
                    state.background.toInt(), state.pattern, size.width, 1f)
            }
            ChoiceButtonGroup("画布背景", SketchPaperPattern.entries, state.pattern, { it.label }, state::setPaperPattern)
            Text("背景随画布移动和缩放，并保留在保存的图片中")
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick(close)) { Text("完成") } })
}
