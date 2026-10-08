package com.kigtts.next.ui.home

enum class ListeningLayout { EXPANDED, SINGLE_LINE, HIDDEN }
const val LISTENING_HANDLE_GAP = 24f
const val LISTENING_COMPACT_HEIGHT = 40f

fun listeningLayout(availableHeight: Float): ListeningLayout = when {
    availableHeight >= 112f -> ListeningLayout.EXPANDED
    availableHeight >= LISTENING_COMPACT_HEIGHT -> ListeningLayout.SINGLE_LINE
    else -> ListeningLayout.HIDDEN
}
