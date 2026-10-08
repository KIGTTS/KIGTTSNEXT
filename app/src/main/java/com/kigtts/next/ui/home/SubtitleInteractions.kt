@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.state.AppViewModel

@Composable
fun Modifier.subtitleInputGestures(model: AppViewModel, enabled: Boolean = true): Modifier = combinedClickable(
    enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null,
    onClickLabel = "输入字幕", onLongClickLabel = "全屏显示字幕",
    onClick = feedbackClick { model.editing(true) }, onLongClick = model::enterSubtitleFullscreen,
)
