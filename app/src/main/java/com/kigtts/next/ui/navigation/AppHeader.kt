package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.kigtts.next.ui.home.homePreviewRegion

@Composable
fun AppHeader(state: AppState, model: AppViewModel, openSettings: () -> Unit) {
    val home = state.destination == Destination.Home
    val recovery by model.safeModeSession.state.collectAsStateWithLifecycle()
    val title = when (val destination = state.destination) {
        Destination.Home -> "KIGTTS"
        Destination.Settings -> "设置"
        Destination.PersonalCards -> "个人信息"
        Destination.PersonalInformationEditor -> "个人信息"
        Destination.ContactFavorites -> "扩列收藏夹"
        is Destination.ContactFavoriteDetail -> "名片详情"
        Destination.Fonts -> "字体"
        is Destination.SettingsDetail -> destination.category.title
        Destination.Plugins -> "插件管理"
        is Destination.PluginPage -> model.pluginPage(destination.pluginId, destination.pageId)?.title ?: "插件页面"
        is Destination.MediaLibrary -> "媒体库"
        is Destination.HtmlFiles -> "页面文件"
        is Destination.HtmlPreview -> "页面预览"
        is Destination.QuickTextReview -> "修改预览"
        is Destination.ContentEditor -> "内容编辑器"
        is Destination.PhraseEditor -> "编辑快捷文本"
        is Destination.CardEditor -> "编辑图片"
        is Destination.CardPreview -> "照片预览"
        Destination.Cards -> "相册"
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
        if (home) SafeModeBrand(recovery.active, Modifier.homePreviewRegion("header-logo"))
        else Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (home) SlotActions(UiSlot.HEADER_END, model.registry, model::invoke, Modifier.weight(1f),
                context = model.context(), revision = state.pluginRevision)
            if (home) IconButton(onClick = feedbackClick(openSettings), modifier = Modifier.testTag("open-settings").homePreviewRegion("header-end"),
                colors = compactPlainIconColors()) { Icon(Icons.Default.Settings, "打开设置") }
        }
    }
}
