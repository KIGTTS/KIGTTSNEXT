package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidFrameStyle
import java.io.File

internal enum class CardMenuAction(val label: String) { SAVE("保存到系统相册"), COPY("复制拍立得"), QUICK("添加到快捷文本"), DELETE("删除拍立得") }

@Composable
internal fun PolaroidCardsGrid(cards: List<PolaroidCard>, imageFile: (String) -> File?, enabled: Boolean,
    preview: (PolaroidCard) -> Unit, edit: (PolaroidCard) -> Unit, share: (PolaroidCard) -> Unit, action: (PolaroidCard, CardMenuAction) -> Unit,
    modifier: Modifier = Modifier, frames: List<PolaroidFrameStyle> = emptyList()) {
    Box(modifier.fillMaxSize()) {
    LazyVerticalGrid(GridCells.Adaptive(270.dp), Modifier.fillMaxSize().testTag("cards-grid"),
        contentPadding = PaddingValues(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(cards, key = { it.id }) { card ->
            var menu by remember(card.id) { mutableStateOf(false) }
            Column(Modifier.animateItem()) {
                PolaroidCanvas(card, imageFile, Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
                    onClick = feedbackClick { preview(card) }, onLongClick = feedbackClick { edit(card) }),
                    frame = frames.firstOrNull { it.id == card.frameStyleId })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    MotionIconButton(feedbackClick { edit(card) }, enabled = enabled) { Icon(Icons.Default.Edit, "编辑拍立得：" + card.title) }
                    MotionIconButton(feedbackClick { share(card) }, enabled = enabled) { Icon(Icons.Default.Share, "分享拍立得：" + card.title) }
                    Box {
                        MotionIconButton(feedbackClick { menu = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, "拍立得操作：" + card.title) }
                        DropdownMenu(menu && enabled, { menu = false }) {
                            CardMenuAction.entries.forEach { operation -> DropdownMenuItem(text = { Text(operation.label) },
                                leadingIcon = { Icon(when (operation) {
                                    CardMenuAction.SAVE -> Icons.Default.Download
                                    CardMenuAction.COPY -> Icons.Default.ContentCopy
                                    CardMenuAction.QUICK -> Icons.Default.TextFields
                                    CardMenuAction.DELETE -> Icons.Default.DeleteOutline
                                }, null) }, onClick = feedbackClick { menu = false; action(card, operation) }) }
                        }
                    }
                }
            }
        }
    }
    EmptyContent(cards.isEmpty(), Icons.Default.Collections, uiText("photo.start_one"), description = uiText("photo.start_hint"))
    }
}
