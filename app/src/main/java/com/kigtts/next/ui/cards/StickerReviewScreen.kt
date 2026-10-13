package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.RegisterPageLoading
import com.kigtts.next.ui.navigation.RegisterPageBackGuard
import com.kigtts.plugin.api.StickerReply
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Only this native page can approve another plugin's destructive catalog transaction. */
@Composable
fun StickerReviewScreen(model: AppViewModel, token: String) {
    val scope = rememberCoroutineScope()
    val preview = remember(token) { model.stickerPlugins.preview(token) }
    val planned = remember(token) { model.stickerPlugins.reviewSnapshot(token) }
    val before = remember(token) { model.stickers.snapshot.value }
    var busy by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    RegisterPageLoading(busy)
    RegisterPageBackGuard(busy) { }
    PredictiveBackHandler(busy) { events -> try { events.collect { } } catch (_: CancellationException) { } }
    val removed = remember(token) { buildList {
        before.cards.filter { old -> planned?.cards?.none { it.id == old.id } == true }.forEach { add("名片：${it.title}") }
        before.items.filter { old -> planned?.items?.none { it.id == old.id } == true }.forEach { add("贴纸：${it.name}") }
        before.libraries.filter { old -> planned?.libraries?.none { it.id == old.id } == true }.forEach { add("贴纸库：${it.name}") }
        before.images.filter { old -> planned?.images?.none { it.id == old.id } == true }.forEach { add("图片：${it.width} × ${it.height}") }
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (preview == null) "预览已失效，请重新准备修改" else "确认以下修改后才会保存。")
        removed.forEach { name -> Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(name); Text("将移除", style = MaterialTheme.typography.bodySmall) }
        } }
        preview?.titles?.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MotionTextButton(feedbackClick {
                busy = true
                scope.launch {
                    try {
                        when (val result = model.stickerPlugins.approve(token)) {
                            is StickerReply.Applied -> { model.message("已保存"); model.navigateBack() }
                            is StickerReply.Failure -> error = result.message
                            else -> error = "修改未完成，请重新读取内容"
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = "保存失败，请重试" }
                    finally { busy = false }
                }
            }, enabled = preview != null && planned != null && !busy) { Text("确认修改") }
            MotionTextButton(feedbackClick(model::navigateBack), enabled = !busy) { Text("取消") }
        }
    }
}
