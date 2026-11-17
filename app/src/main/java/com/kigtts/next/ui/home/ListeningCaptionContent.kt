package com.kigtts.next.ui.home

import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import com.kigtts.next.i18n.uiText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Wrap at the actual panel width. Short captions are centered; long captions preserve scrollable history. */
@Composable
internal fun ListeningCaptionContent(text: String, fontSize: Float, singleLine: Boolean, modifier: Modifier = Modifier,
    finalOnly: Boolean = false, reducedMotion: Boolean = false, animateReflow: Boolean = true) {
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
    LaunchedEffect(scroll, follow, singleLine, reducedMotion, animateReflow) {
        if (singleLine) return@LaunchedEffect
        launch {
            snapshotFlow { scroll.value to scroll.maxValue }.collect { (value, max) -> follow.observe(value, max) }
        }
        snapshotFlow { CaptionScrollTarget(scroll.maxValue, dragged, follow.following) }.collectLatest { target ->
            if (target.following && !target.dragged && target.maximum != Int.MAX_VALUE && scroll.value < target.maximum) {
                if (reducedMotion || !animateReflow) scroll.scrollTo(target.maximum)
                else scroll.animateScrollTo(target.maximum, tween(170))
            }
        }
    }
    val empty = text.isBlank()
    val caption = if (empty) uiText("listening.standby")
        else if (singleLine) text.substringAfterLast('\n') else text
    val colors = MaterialTheme.colorScheme
    val historyColor = colors.onSurfaceVariant.copy(alpha = .74f)
    val paragraph = remember(caption, empty, finalOnly, historyColor) {
        buildAnnotatedString {
            append(caption)
            val last = if (!empty && finalOnly) caption.lastIndexOf('\n') else -1
            if (last >= 0) addStyle(SpanStyle(color = historyColor), 0, last)
        }
    }
    var viewport by remember { mutableIntStateOf(0) }
    val motion = rememberListeningCaptionMotion(empty, CaptionVisuals(finalOnly, reducedMotion, animateReflow), viewport)
    Box(modifier.clipToBounds().onSizeChanged { viewport = it.height }, contentAlignment = Alignment.CenterStart) {
        Text(paragraph, Modifier.fillMaxWidth().testTag("listening-content").then(
            if (singleLine) Modifier else Modifier.nestedScroll(gesture).verticalScroll(scroll)).then(motion.modifier),
            fontSize = resolvedSize.sp, lineHeight = (resolvedSize * 1.35f).sp,
            softWrap = !singleLine, maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            color = if (empty) colors.onSurfaceVariant.copy(alpha = .46f) else if (finalOnly) colors.onSurface else colors.onSurfaceVariant,
            onTextLayout = motion.onLayout)
    }
}
private data class CaptionScrollTarget(val maximum: Int, val dragged: Boolean, val following: Boolean)
