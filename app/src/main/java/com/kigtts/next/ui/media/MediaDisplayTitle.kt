package com.kigtts.next.ui.media

import androidx.compose.runtime.Composable
import com.kigtts.next.i18n.uiText
import com.kigtts.next.media.MediaEntry
import com.kigtts.next.media.MediaKind
import com.kigtts.next.state.QuickPhrase

/** Playback resources use the user's card caption or a media label, never a filesystem name. */
internal fun mediaDisplayTitle(entry: MediaEntry, phrases: List<QuickPhrase>, fallback: String = entry.kind.label): String {
    if(entry.kind !in setOf(MediaKind.VIDEO,MediaKind.AUDIO))return entry.name
    val phrase=phrases.firstOrNull { it.mediaId==entry.id }
    return (phrase?.title?.takeIf { it.isNotBlank() } ?: phrase?.text)?.trim()?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }?.take(48) ?: fallback
}

@Composable
internal fun MediaKind.displayLabel(): String = when (this) {
    MediaKind.IMAGE -> uiText("media.kind_image")
    MediaKind.MOTION_PHOTO -> uiText("media.kind_motion_photo")
    MediaKind.VIDEO -> uiText("media.kind_video")
    MediaKind.HTML -> "HTML"
    MediaKind.WEB -> uiText("media.kind_web")
    MediaKind.AUDIO -> uiText("media.kind_audio")
}
