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

internal enum class CardMenuAction(val textKey: String) {
    SAVE("photo.album_save_action"),
    COPY("photo.copy_polaroid"),
    QUICK("quick.add_from_text"),
    DELETE("photo.delete_polaroid"),
}

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
                    MotionIconButton(feedbackClick { edit(card) }, enabled = enabled) { Icon(Icons.Default.Edit, uiText("accessibility.edit_photo_named", "name" to card.title)) }
                    MotionIconButton(feedbackClick { share(card) }, enabled = enabled) { Icon(Icons.Default.Share, uiText("accessibility.share_photo_named", "name" to card.title)) }
                    Box {
                        MotionIconButton(feedbackClick { menu = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, uiText("accessibility.photo_actions_named", "name" to card.title)) }
                        DropdownMenu(menu && enabled, { menu = false }) {
                            CardMenuAction.entries.forEach { operation -> DropdownMenuItem(text = { Text(uiText(operation.textKey)) },
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
