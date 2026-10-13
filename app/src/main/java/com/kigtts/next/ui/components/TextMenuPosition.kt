package com.kigtts.next.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider

/** Selection bounds are relative to the provider's current native window, including dialogs. */
internal class TextMenuPosition(private val bounds:()->Rect,private val top:Int,private val bottom:Int,private val margin:Int,
    private val shadowInset:Int=0,private val leftInset:Int=0,private val rightInset:Int=0):PopupPositionProvider {
    override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
        val rect=bounds()
        val left=anchorBounds.left+rect.center.x.toInt()-popupContentSize.width/2
        // The native window includes shadow padding; keep the visible surface at the original selection gap.
        val above=anchorBounds.top+rect.top.toInt()-popupContentSize.height-margin+shadowInset
        val y=if(above>=top+margin)above else anchorBounds.top+rect.bottom.toInt()+margin-shadowInset
        val minimumLeft=leftInset+margin
        return IntOffset(left.coerceIn(minimumLeft,maxOf(minimumLeft,windowSize.width-rightInset-popupContentSize.width-margin)),
            y.coerceIn(top+margin,maxOf(top+margin,windowSize.height-bottom-popupContentSize.height-margin)))
    }
}
