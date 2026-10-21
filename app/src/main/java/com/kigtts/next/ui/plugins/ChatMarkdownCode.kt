package com.kigtts.next.ui.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun ChatMarkdownCode(code: String, info: String, small: Boolean) {
    val clipboard = LocalClipboardManager.current
    Surface(Modifier.fillMaxWidth().testTag("chat-markdown-code"), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(info.substringBefore(' ').ifBlank { "代码" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton(feedbackClick { clipboard.setText(AnnotatedString(code)) }) { Icon(Icons.Default.ContentCopy, "复制代码", Modifier.size(18.dp)) }
            }
            Text(code.trimEnd('\n'), Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                style = (if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium).copy(fontFamily = FontFamily.Monospace))
        }
    }
}
