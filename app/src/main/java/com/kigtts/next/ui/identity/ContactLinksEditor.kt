package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.ui.components.*

@Composable
internal fun ContactLinksEditor(links: List<ContactLink>, change: (List<ContactLink>) -> Unit) {
    var editing by rememberSaveable { mutableIntStateOf(-2) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("联系方式", style = MaterialTheme.typography.titleSmall)
        links.forEachIndexed { index, link ->
            Surface(onClick = feedbackClick { editing = index }, shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialContactIcon(link, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(link.label, style = MaterialTheme.typography.labelLarge)
                        Text(link.value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(feedbackClick { change(links.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Default.Close, "移除${link.label}")
                    }
                }
            }
        }
        if (links.size < 8) OutlinedButton(feedbackClick { editing = -1 }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加联系方式")
        }
    }
    if (editing >= -1) {
        val existing = links.getOrNull(editing)
        ContactLinkEditor(existing, { editing = -2 }) { updated ->
            change(if (editing < 0) links + updated else links.mapIndexed { i, item -> if (i == editing) updated else item })
            editing = -2
        }
    }
}

@Composable
private fun ContactLinkEditor(value: ContactLink?, close: () -> Unit, save: (ContactLink) -> Unit) {
    var label by rememberSaveable(value) { mutableStateOf(value?.label.orEmpty()) }
    var account by rememberSaveable(value) { mutableStateOf(value?.value.orEmpty()) }
    MotionAlertDialog(close, title = { Text(if (value == null) "添加联系方式" else "修改联系方式") },
        icon = { SocialContactIcon(ContactLink(label, account)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { label = it.take(24) }, Modifier.fillMaxWidth(),
                    label = { Text("平台或名称") }, placeholder = { Text("微信、QQ、主页…") }, singleLine = true)
                OutlinedTextField(account, { account = it.take(512) }, Modifier.fillMaxWidth(), label = { Text("账号或链接") }, maxLines = 3)
            }
        }, confirmButton = {
            TextButton(dialogFeedbackClick { save(ContactLink(label.trim(), account.trim())) },
                enabled = label.isNotBlank() && account.isNotBlank()) { Text("保存") }
        }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text("取消") } })
}
