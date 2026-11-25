package com.kigtts.next.state

enum class MediaQuickTextCollapse { KEEP, SINGLE_ROW, COLLAPSE }

/** Change only the visible dock. Its saved size is used again when the user expands it. */
internal fun AppState.afterOpeningMedia(): AppState {
    if (panel != HomePanel.QUICK_TEXT) return this
    return when (mediaQuickTextCollapse) {
        MediaQuickTextCollapse.KEEP -> this
        MediaQuickTextCollapse.SINGLE_ROW -> copy(panelHeight = HomePanelSizes.PEEK, mediaSingleRow = true)
        MediaQuickTextCollapse.COLLAPSE -> copy(panel = null, quickTextEditing = false, mediaSingleRow = false)
    }
}
