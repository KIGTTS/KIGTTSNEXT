package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.compactPlainIconColors
import com.kigtts.next.R
import com.kigtts.next.state.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.UiSlot

@Composable
fun AppHeader(state: AppState, model: AppViewModel, openSettings: () -> Unit) {
    val home = state.destination == Destination.Home
    val title = when (val destination = state.destination) {
        Destination.Home -> "KIGTTS"
        Destination.Settings -> "设置"
        is Destination.SettingsDetail -> destination.category.title
        Destination.Plugins -> "插件管理"
        is Destination.PluginPage -> model.registry.plugin(destination.pluginId)?.page(destination.pageId, model.context())?.title ?: "插件页面"
        is Destination.MediaLibrary -> "媒体库"
        is Destination.HtmlFiles -> "页面文件"
        is Destination.HtmlPreview -> "页面预览"
        is Destination.QuickTextReview -> "修改预览"
        is Destination.ContentEditor -> "内容编辑器"
        is Destination.PhraseEditor -> "编辑快捷文本"
        is Destination.CardEditor -> "编辑拍立得"
        is Destination.CardPreview -> "拍立得预览"
        Destination.Cards -> "拍立得"
        is Destination.CardCamera -> "拍摄"
        is Destination.StickerReview -> "修改预览"
        is Destination.StickerLibrary -> "贴纸库"
    }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (home) com.kigtts.next.ui.home.AutomaticReadButton(state, model)
            else IconButton(onClick = feedbackClick(model::navigateBack),
                modifier=Modifier.testTag("auto-playback"), colors = compactPlainIconColors()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回上一页")
            }
            if (home) SlotActions(UiSlot.HEADER_START, model.registry, model::invoke, Modifier.weight(1f),
                context = model.context(), revision = state.pluginRevision)
        }
        if (home) Icon(painterResource(R.drawable.logo_horizontal), contentDescription = "KIGTTS 横版 Logo",
            modifier = Modifier.width(128.dp).height(32.dp), tint = MaterialTheme.colorScheme.onSurface)
        else Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (home) SlotActions(UiSlot.HEADER_END, model.registry, model::invoke, Modifier.weight(1f),
                context = model.context(), revision = state.pluginRevision)
            if (home) IconButton(onClick = feedbackClick(openSettings), modifier = Modifier.testTag("open-settings"),
                colors = compactPlainIconColors()) { Icon(Icons.Default.Settings, "打开设置") }
        }
    }
}
