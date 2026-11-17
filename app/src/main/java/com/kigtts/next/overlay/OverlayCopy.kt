package com.kigtts.next.overlay

/** Use route identity for stock shortcuts. External app names, custom titles and badges are owned by their sources. */
internal fun OverlayShortcut.localizedTitle(copy: (String) -> String): String {
    if (owner != null || component != null) return title
    val key = when (id) {
        "home" -> "overlay.shortcut.subtitles"
        "camera" -> "nav.camera"
        "cards" -> "nav.album"
        "sketch" -> "overlay.shortcut.sketch"
        "listen" -> "overlay.shortcut.listen"
        "caption-window" -> "listening.open_window"
        "speak" -> "overlay.shortcut.read"
        "overlay-settings" -> "settings.category.overlay"
        else -> return title
    }
    return copy(key)
}
