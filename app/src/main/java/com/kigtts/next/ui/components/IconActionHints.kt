@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay

private class IconHint {
    var visible by mutableStateOf(false)
    var bounds = androidx.compose.ui.geometry.Rect.Zero
}
private val LocalIconHint = staticCompositionLocalOf<IconHint?> { null }

/** No layout wrapper: parent alignment, weight and native touch targets stay intact. */
@Composable internal fun IconActionHint(label: String, hasLongAction: Boolean = false, content: @Composable () -> Unit) {
    if (hasLongAction) { content(); return }
    val hint = remember(label) { IconHint() }
    CompositionLocalProvider(LocalIconHint provides hint) { content() }
    if (hint.visible) {
        LaunchedEffect(hint) { delay(2200); hint.visible = false }
        Popup(popupPositionProvider = remember(hint) { object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val rect = hint.bounds
                val x = (rect.center.x - popupContentSize.width / 2).toInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val y = (rect.top - popupContentSize.height - 8).toInt().let { if (it >= 0) it else rect.bottom.toInt() + 8 }
                return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
            }
        } }, onDismissRequest = { hint.visible = false }, properties = PopupProperties(focusable = false)) {
            Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface, shadowElevation = 4.dp) {
                Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Native clickable consuming down is not a long-press cancellation. */
@Composable internal fun Modifier.iconLongAction(enabled: Boolean, action: (() -> Unit)?): Modifier {
    val hint = LocalIconHint.current
    val callback = action ?: hint?.let { { it.visible = true } }
    val latest = rememberUpdatedState(callback)
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    if (callback == null || !enabled) return this
    return onGloballyPositioned { hint?.bounds = it.boundsInWindow() }
        .semantics { onLongClick { latest.value?.invoke(); true } }
        .pointerInput(enabled, action != null) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || event.changes.count { it.pressed } > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                    }
                }
                if (cancelled == null) {
                    currentEvent.changes.forEach { it.consume() }
                    feedback.value.performHapticFeedback(HapticFeedbackType.LongPress)
                    latest.value?.invoke()
                    do { val event = awaitPointerEvent(PointerEventPass.Initial); event.changes.forEach { it.consume() } }
                    while (event.changes.any { it.pressed })
                }
            }
        }
}
