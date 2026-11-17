package com.kigtts.next.ui.home

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.kigtts.next.i18n.uiText

/** Wrap at the actual panel width. Short captions sit at the bottom; long captions keep a readable history. */
@Composable
internal fun ListeningCaptionContent(text: String, fontSize: Float, singleLine: Boolean, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val resolvedSize = if (singleLine) fontSize.coerceAtMost(18f) else fontSize
    val follow = rememberSaveable(saver = Saver<ListeningFollowState, Boolean>(save = { it.following }, restore = { ListeningFollowState(it) })) {
        ListeningFollowState()
    }
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val gesture = remember(follow) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f) follow.userMovedTowardHistory()
            return Offset.Zero
        }
    } }
    LaunchedEffect(scroll, follow, singleLine) {
        if (singleLine) return@LaunchedEffect
        snapshotFlow { ScrollPosition(scroll.value, scroll.maxValue, dragged, follow.following) }.collect { position ->
            follow.observe(position.value, position.maximum)
            if (follow.following && !position.dragged && position.maximum != Int.MAX_VALUE && position.value < position.maximum) {
                // A direct offset update is inexpensive during frequent partial updates and cannot keep
                // an animation alive that fights the next touch gesture.
                scroll.scrollTo(position.maximum)
            }
        }
    }
    val empty = text.isBlank()
    val caption = if (empty) uiText("listening.standby")
        else if (singleLine) text.substringAfterLast('\n') else text
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.clipToBounds(), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.BottomStart) {
        Text(caption, Modifier.fillMaxWidth().testTag("listening-content").then(
            if (singleLine) Modifier else Modifier.nestedScroll(gesture).verticalScroll(scroll)),
            fontSize = resolvedSize.sp, lineHeight = (resolvedSize * 1.35f).sp,
            softWrap = !singleLine, maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            color = if (empty) foreground.copy(alpha = .46f) else foreground)
    }
}
private data class ScrollPosition(val value: Int, val maximum: Int, val dragged: Boolean, val following: Boolean)
