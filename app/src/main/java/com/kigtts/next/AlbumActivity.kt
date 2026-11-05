package com.kigtts.next

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettingsStore
import com.kigtts.next.ui.album.DeviceAlbumScreen
import com.kigtts.next.ui.cards.CardEditorScreen
import com.kigtts.next.ui.cards.CardPageScaffold
import com.kigtts.next.ui.media.WebAndroidPermissionHost
import com.kigtts.next.cards.exportPolaroidMedia
import kotlinx.coroutines.*

/** An independent Android gallery / picker / editor entry; no Home navigation or plugin startup UI. */
class AlbumActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val incoming = intent.data?.takeIf { it.scheme == "content" }
        val editing = intent.action == Intent.ACTION_EDIT
        val picking = intent.action in setOf(Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT)
        if (editing && incoming == null) { setResult(Activity.RESULT_CANCELED); finish(); return }
        val host = HostPluginRuntime.get(application)
        val theme = ThemeSettingsStore(this).load()
        setContent {
            KigttsTheme(theme) {
                WebAndroidPermissionHost()
                val scope = rememberCoroutineScope()
                var editor by rememberSaveable { mutableStateOf<String?>(null) }
                var failure by remember { mutableStateOf<String?>(null) }
                var opening by remember { mutableStateOf(false) }
                fun open(uri: Uri) { if (!opening) scope.launch {
                    opening = true
                    try { editor = host.cards.documents.open(uri) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { failure = error.message ?: "无法读取图片" }
                    finally { opening = false }
                } }
                LaunchedEffect(incoming, editing) { if (editing && incoming != null && editor == null) open(incoming) }
                val id = editor
                if (id != null) CardPageScaffold("编辑图片", { if (editing) finish() else editor = null }, subtitle = null, avoidKeyboard = false, compactBar = true) {
                    CardEditorScreen(host.cards, host.stickers, id, { failure = it }, onSaved = { card ->
                        scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) {
                                    val output = @Suppress("DEPRECATION") (intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT))
                                    if (output != null) {
                                        require(output.scheme == "content") { "无法写入此保存位置" }
                                        val file = exportPolaroidMedia(host.stickers, card, maximumHeight = com.kigtts.next.cards.galleryPolaroidHeight(host.stickers, card))
                                        requireNotNull(contentResolver.openOutputStream(output, "w")).use { target -> file.inputStream().use { it.copyTo(target) } }
                                        output
                                    } else host.cards.systemAlbum.save(card.id)
                                }
                                if (editing) { returnImages(listOf(uri)); finish() } else editor = null
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) { failure = error.message ?: "未能保存图片" }
                        }
                    })
                } else DeviceAlbumScreen({ finish() }, ::open, message = { failure = it },
                    choose = if (picking) ({ media -> returnImages(media.map { it.uri }); finish() }) else null,
                    multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false), initialUri = if (!editing) incoming else null,
                    imagesOnly = picking && intent.type.orEmpty().startsWith("image/"),
                    videosOnly = picking && intent.type.orEmpty().startsWith("video/"))
                if (opening) AlertDialog({}, title = { Text("正在打开图片") }, text = { CircularProgressIndicator() }, confirmButton = {})
                failure?.let { text -> AlertDialog({ failure = null }, text = { Text(text) }, confirmButton = { TextButton({ failure = null }) { Text("知道了") } }) }
            }
        }
    }
    private fun returnImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val data = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
            clipData = ClipData.newUri(contentResolver, "照片", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        }
        setResult(Activity.RESULT_OK, data)
    }
}
