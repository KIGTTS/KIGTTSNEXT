package com.kigtts.next.ui.components

import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider

/** Selection bounds are relative to the provider's current native window, including dialogs. */
internal class TextMenuPosition(private val bounds: () -> Rect, private val top: Int, private val bottom: Int, private val margin: Int,
    private val shadowInset: Int = 0, private val leftInset: Int = 0, private val rightInset: Int = 0,
    private val keepInContent: Boolean = false,
    private val visibleWindow: () -> IntRect? = { null }) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
        val rect=bounds()
        val viewport = visibleWindow() ?: IntRect(leftInset, top, windowSize.width - rightInset, windowSize.height - bottom)
        val minimumLeft = viewport.left + margin
        val contentTop = maxOf(viewport.top, anchorBounds.top)
        val contentBottom = minOf(viewport.bottom, anchorBounds.bottom)
        val fitsInContent = keepInContent && contentBottom - contentTop >= popupContentSize.height + margin * 2
        val verticalTop = if (fitsInContent) contentTop else viewport.top
        val verticalBottom = if (fitsInContent) contentBottom else viewport.bottom
        val minimumTop = verticalTop + margin
        val maximumTop = maxOf(minimumTop, verticalBottom - popupContentSize.height - margin)
        val selectedTop = anchorBounds.top + rect.top.toInt()
        val selectedBottom = anchorBounds.top + rect.bottom.toInt()
        val left = anchorBounds.left + rect.center.x.toInt() - popupContentSize.width / 2
        val above = selectedTop - popupContentSize.height - margin + shadowInset
        val below = selectedBottom + margin - shadowInset
        val moreSpaceAbove = selectedTop - verticalTop > verticalBottom - selectedBottom
        val y = if (above >= minimumTop || (below > maximumTop && moreSpaceAbove)) above else below
        return IntOffset(
            left.coerceIn(minimumLeft, maxOf(minimumLeft, viewport.right - popupContentSize.width - margin)),
            y.coerceIn(minimumTop, maximumTop),
        )
    }
}

internal fun textMenuVisibleWindow(view: View): IntRect? {
    val frame = android.graphics.Rect()
    view.getWindowVisibleDisplayFrame(frame)
    if (frame.isEmpty) return null
    // The visible display frame is in screen coordinates. Compose anchors are in the host window.
    // On older Android the visible frame already excludes the IME and can have a nonzero origin.
    val screen = IntArray(2).also(view::getLocationOnScreen)
    val window = IntArray(2).also(view::getLocationInWindow)
    frame.offset(window[0] - screen[0], window[1] - screen[1])
    return IntRect(frame.left, frame.top, frame.right, frame.bottom)
}
