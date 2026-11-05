package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.identity.ProfileTileImage
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.feedbackClick
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.*

@Composable internal fun CloudNativePreview(model: AppViewModel, session: CloudNativeSession) {
    val identities by model.identities.items.collectAsState()
    if (session.kind == "profile" && identities.isNotEmpty()) {
        FaceSharePreview(identities.first(), emptyList(), model.stickers)
    } else if (session.kind == "photos") {
        val manifest = remember(session) { JSONObject(File(session.root, "share.json").readText()).getJSONArray("files") }
        val images = remember(session) { (0 until minOf(3, manifest.length())).map { File(session.root, manifest.getJSONObject(it).getString("path")) } }
        Box(Modifier.fillMaxWidth().height(230.dp).testTag("cloud-native-photos"), contentAlignment = Alignment.Center) {
            images.indices.reversed().forEach { index ->
                ProfileTileImage(images[index], Modifier.width(240.dp).height(210.dp).graphicsLayer {
                    rotationZ = when(index) { 1 -> -6f; 2 -> 5f; else -> 0f }
                }.clip(RoundedCornerShape(16.dp)), "分享的照片", fit = true)
            }
        }
    } else Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FormatQuote, null, Modifier.size(32.dp))
            Text(session.title.ifBlank { "快捷文本" }, style = MaterialTheme.typography.headlineSmall)
            Text("分享快捷文本", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable internal fun ColumnScope.CloudNativeReceived(model: AppViewModel, session: CloudNativeSession) {
    val scope = rememberCoroutineScope()
    val shown = session.files.filter { session.kind != "profile" || it.name == "profile.pdf" }
    if (session.files.isEmpty()) {
        Column(Modifier.fillMaxWidth().height(220.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.MoveToInbox, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp)); Text("等待对方分享", style = MaterialTheme.typography.titleMedium)
            Text("扫码、碰一碰，或让对方点选你的头像", style = MaterialTheme.typography.bodyMedium)
        }
    }
    session.profile?.let { profile ->
        Text(profile.optString("nickname", "KIGTTS 用户"), style = MaterialTheme.typography.headlineSmall)
        profile.optString("bio").takeIf { it.isNotBlank() }?.let { Text(it) }
        session.files.firstOrNull { it.name == profile.optString("banner") }?.let { banner ->
            session.cached[banner.index]?.let { ProfileTileImage(it.file, Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large), "对方的名片", fit = true) }
        }
        Button(feedbackClick(session::saveContact), Modifier.fillMaxWidth(), enabled = !session.savingContact) {
            Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(if (session.savingContact) "正在接收名片" else "加入扩列收藏夹")
        }
    }
    shown.forEach { file ->
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().testTag("cloud-native-file-${file.index}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                session.cached[file.index]?.takeIf { file.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }?.let {
                    ProfileTileImage(it.file, Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium), "收到的照片", fit = true)
                }
                Text(file.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(android.text.format.Formatter.formatShortFileSize(model.getApplication(), file.bytes), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(feedbackClick { session.get(file.index, save = true) }, enabled = file.index !in session.saved) {
                        Text(if (file.index in session.saved) "已接收" else if (file.name.endsWith(".kigcard")) "添加快捷文本" else "保存")
                    }
                }
            }
        }
    }
}
