package com.kigtts.next.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick

@Composable internal fun ProfileShareFormatDialog(sharing: SharingCoordinator) {
    var includeSource by remember { mutableStateOf(false) }
    MotionAlertDialog(onDismissRequest = sharing::dismissProfileFormat,
        title = { Text("分享个人主页") },
        text = {
            Column {
                ProfileShareFormat.entries.forEach { format ->
                    Surface(onClick = dialogFeedbackClick { sharing.shareProfile(format, includeSource) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(format.label) }, supportingContent = { Text(when (format) {
                            ProfileShareFormat.PDF -> "可翻页查看，适合保存和打印"
                            ProfileShareFormat.MHTML -> "可点开图片和联系方式，也可导入 KIGTTS"
                            ProfileShareFormat.LONG_IMAGE -> "方便直接查看，链接汇成底部二维码"
                        }) })
                    }
                }
                Surface(onClick = { includeSource = !includeSource }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text("长图嵌入源数据") },
                        supportingContent = { Text("会增大文件，保留原文件时可恢复图片磁贴") },
                        leadingContent = { Checkbox(includeSource, onCheckedChange = null) })
                }
            }
        },
        confirmButton = { TextButton(dialogFeedbackClick(sharing::dismissProfileFormat)) { Text("取消") } })
}
