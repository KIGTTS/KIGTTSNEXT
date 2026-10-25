package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*

@Composable internal fun ContactPlatformPicker(close: () -> Unit, choose: (SocialPlatform) -> Unit) {
    var query by remember { mutableStateOf("") }
    MotionAlertDialog(close, title = { Text("添加联系方式") }, text = {
        Column(Modifier.heightIn(max = 440.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索平台") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn {
                items(SocialPlatform.entries.filter { query.isBlank() || it.displayName.contains(query, true) || it.labels.any { name -> name.contains(query, true) } }) { platform ->
                    TextButton(feedbackClick { choose(platform) }, Modifier.fillMaxWidth()) {
                        SocialContactIcon(ContactLink(platform.displayName, ""), Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp)); Text(platform.displayName, Modifier.weight(1f))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick(close)) { Text("取消") } })
}
