package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.compactPlainIconColors
import com.kigtts.next.R
import com.kigtts.next.state.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.UiSlot

@Composable
fun AppHeader(state: AppState, model: AppViewModel, drawer: () -> Unit) {
    val home = state.destination == Destination.Home
    val title = when (val destination = state.destination) {
        Destination.Home -> "KIGTTS"
        Destination.Settings -> "设置"
        is Destination.SettingsDetail -> destination.category.title
        Destination.Plugins -> "插件管理"
        is Destination.PluginPage -> model.registry.plugin(destination.pluginId)?.page(destination.pageId, model.context())?.title ?: "插件页面"
    }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = feedbackClick { if (home) model.message("当前使用占位引擎，不播放声音") else model.navigateBack() },
                colors = compactPlainIconColors()) {
                Icon(if (home) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.ArrowBack, if (home) "声音状态" else "返回上一页")
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
            IconButton(onClick = feedbackClick(drawer), colors = compactPlainIconColors()) { Icon(Icons.Default.Menu, "打开抽屉菜单") }
        }
    }
}
