package com.kigtts.next.ui.identity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun ProfileTileEditor(tile: ProfileTile?, contacts: List<ContactLink>, stickers: StickerRepository,
    close: () -> Unit, save: (ProfileTile) -> Unit) {
    var kind by remember(tile?.id) { mutableStateOf(tile?.kind) }
    var title by remember(tile?.id) { mutableStateOf(tile?.title.orEmpty()) }
    var text by remember(tile?.id) { mutableStateOf(tile?.text.orEmpty()) }
    var contact by remember(tile?.id) { mutableStateOf(tile?.contact.orEmpty()) }
    var image by remember(tile?.id) { mutableStateOf(tile?.image.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch {
        working = true
        try { image = withContext(Dispatchers.IO) {
            // Preserve the original GIF bytes; the generic sticker importer rasterizes images.
            val directory = File(stickers.context.filesDir, "profile-images").apply { mkdirs() }
            val id = java.util.UUID.randomUUID().toString()
            val file = File(directory, id)
            try {
                stickers.context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536); var count = 0L
                    while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= 20_000_000) { "请选择 20 MB 以内的图片" }; output.write(buffer, 0, n) }
                } }
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, options)
                require(options.outWidth in 1..16384 && options.outHeight in 1..16384) { "图片格式无法打开" }
                id
            } catch (e: Exception) { file.delete(); throw e }
        } }
        catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) { error = e.message ?: "图片未能打开" }
        finally { working = false }
    } }
    val names = linkedMapOf(ProfileTileKind.LINK to "链接", ProfileTileKind.CONTACT to "社媒", ProfileTileKind.TITLE to "标题",
        ProfileTileKind.TEXT to "文字", ProfileTileKind.IMAGE to "图片 / GIF")
    MotionAlertDialog(close, title = { Text(if (tile == null) "添加磁贴" else "编辑磁贴") }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kind == null) names.forEach { (value, name) -> OutlinedButton(feedbackClick { kind = value }, Modifier.fillMaxWidth()) { Text(name) } }
            else {
                Text(names[kind] ?: "状态", style = MaterialTheme.typography.titleSmall)
                if (kind != ProfileTileKind.IMAGE) OutlinedTextField(title, { title = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(if (kind == ProfileTileKind.TITLE) "标题" else "名称") }, singleLine = true)
                when (kind) {
                    ProfileTileKind.CONTACT -> {
                        if (contacts.isEmpty()) Text("请先添加联系方式")
                        contacts.forEach { link -> Row(Modifier.fillMaxWidth()) {
                            RadioButton(contact == link.value, feedbackClick { contact = link.value })
                            TextButton(feedbackClick { contact = link.value }) { SocialContactIcon(link); Spacer(Modifier.width(8.dp)); Text(link.label) }
                        } }
                    }
                    ProfileTileKind.LINK -> OutlinedTextField(text, { text = it.take(2048) }, Modifier.fillMaxWidth(), label = { Text("网址") }, singleLine = true)
                    ProfileTileKind.IMAGE -> {
                        if (image.isNotBlank()) ProfileTileImage(profileImageFile(stickers, image), Modifier.fillMaxWidth().height(160.dp))
                        FilledTonalButton(feedbackClick { if (!working) picker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = !working) { Icon(Icons.Default.Image, null); Text(if (working) "正在打开…" else "选择图片或 GIF") }
                    }
                    ProfileTileKind.TITLE -> Unit
                    else -> OutlinedTextField(text, { text = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("内容") }, minLines = 2)
                }
                if (tile == null) TextButton(feedbackClick { kind = null }) { Text("更换类型") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }, confirmButton = { if (kind != null) TextButton(feedbackClick {
        if (working) return@feedbackClick
        val selected = kind ?: return@feedbackClick
        if (selected == ProfileTileKind.LINK && safeProfileLink(text) == null) { error = "请输入 http 或 https 网址"; return@feedbackClick }
        if (selected == ProfileTileKind.CONTACT && contact.isBlank()) { error = "请选择联系方式"; return@feedbackClick }
        if (selected == ProfileTileKind.IMAGE && image.isBlank()) { error = "请选择图片"; return@feedbackClick }
        if (selected == ProfileTileKind.TITLE && title.isBlank()) { error = "请输入标题"; return@feedbackClick }
        save((tile ?: ProfileTile()).copy(kind = selected, title = title.trim(), text = text.trim(), contact = contact, image = image,
            columns = if (selected == ProfileTileKind.TITLE) 4 else tile?.columns ?: 2, rows = if (selected == ProfileTileKind.TITLE) 1 else tile?.rows ?: 2))
    }) { Text("完成") } }, dismissButton = { TextButton(dialogFeedbackClick(close)) { Text("取消") } })
}

internal fun safeProfileLink(text: String): String? = runCatching { java.net.URI(text.trim()).let { uri ->
    text.trim().takeIf { uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null && it.none(Char::isISOControl) }
} }.getOrNull()
