package com.kigtts.next.ui.identity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun ContactLinkEditor(value: ContactLink?, platform: SocialPlatform, close: () -> Unit, save: (ContactLink) -> Unit) {
    var label by rememberSaveable(value, platform) { mutableStateOf(value?.label ?: platform.displayName) }
    var account by rememberSaveable(value, platform) { mutableStateOf(value?.value.orEmpty()) }
    var qr by rememberSaveable(value, platform) { mutableStateOf(value?.qrContent.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true; error = null
            try { qr = withContext(Dispatchers.IO) { readContactQr(context, uri) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "没有识别到二维码，请选择清晰的二维码图片" }
            finally { loading = false }
        }
    }
    MotionAlertDialog(close, title = { Text(if (value == null) "添加${platform.displayName}" else "修改${value.label}") },
        icon = { SocialContactIcon(ContactLink(label, account)) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (platform == SocialPlatform.LINK) OutlinedTextField(label, { label = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
                OutlinedTextField(account, { account = it.take(512) }, Modifier.fillMaxWidth(), label = { Text("账号或链接") }, maxLines = 3)
                OutlinedTextField(qr, { qr = it.take(2048) }, Modifier.fillMaxWidth(), label = { Text("二维码内容（可选）") }, maxLines = 3)
                OutlinedButton(feedbackClick { picker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = !loading) {
                    Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(if (loading) "正在识别…" else "从二维码图片读取")
                }
                Text("微信等平台可选择个人二维码图片；仅填写账号时，二维码展示账号文本", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(dialogFeedbackClick { save(ContactLink(label.trim(), account.trim().ifBlank { qr.trim() }, qr.trim().takeIf(String::isNotBlank))) },
                enabled = label.isNotBlank() && (account.isNotBlank() || qr.isNotBlank()) && !loading) { Text("保存") }
        }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text("取消") } })
}
