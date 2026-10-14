package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** 0: handle, 1: photographs, 2: albums. Only the handle owns the sheet gesture. */
internal val AlbumSheetSaver = Saver<AnchoredDraggableState<Int>, Int>(
    save = { it.targetValue }, restore = { AnchoredDraggableState(it.coerceIn(0, 2)) })

@Composable internal fun rememberCardAlbumSheet(initial: Int = 0) = rememberSaveable(saver = AlbumSheetSaver) {
    AnchoredDraggableState(initial)
}
