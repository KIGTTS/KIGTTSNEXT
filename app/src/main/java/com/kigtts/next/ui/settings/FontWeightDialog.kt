package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*

@Composable internal fun FontWeightDialog(font: InstalledFont, axis: FontWeightAxis, weight: Int,
    dismiss: () -> Unit, confirm: (Int) -> Unit) {
    var draft by remember(font.key) { mutableIntStateOf(axis.clamp(weight)) }
    val face = rememberAppearanceFont(font.key, draft)
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(font.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("你好，KIGTTS\nAa 123", Modifier.fillMaxWidth(),
                style = face.apply(TextStyle(fontSize = 28.sp, lineHeight = 38.sp)))
            FontWeightControl(axis, draft) { draft = it }
        }
    }, confirmButton = { MotionTextButton(onClick = dialogFeedbackClick { confirm(draft) }) { Text("完成") } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(dismiss)) { Text("取消") } })
}
