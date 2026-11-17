@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.state.AppViewModel

@Composable
fun Modifier.subtitleInputGestures(model: AppViewModel, enabled: Boolean = true): Modifier {
    val copy = rememberUiCopy()
    return combinedClickable(
    enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null,
    onClickLabel = copy("subtitle.input"), onLongClickLabel = copy("subtitle.show_fullscreen"),
    onClick = feedbackClick { model.editing(true) }, onLongClick = model::enterSubtitleFullscreen,
)
}
