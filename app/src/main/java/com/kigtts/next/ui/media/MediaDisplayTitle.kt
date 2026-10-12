package com.kigtts.next.ui.media

import com.kigtts.next.media.MediaEntry
import com.kigtts.next.media.MediaKind
import com.kigtts.next.state.QuickPhrase

/** Playback resources use the user's card caption or a media label, never a filesystem name. */
internal fun mediaDisplayTitle(entry:MediaEntry,phrases:List<QuickPhrase>):String {
    if(entry.kind !in setOf(MediaKind.VIDEO,MediaKind.AUDIO))return entry.name
    val phrase=phrases.firstOrNull { it.mediaId==entry.id }
    return (phrase?.title?.takeIf { it.isNotBlank() } ?: phrase?.text)?.trim()?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }?.take(48) ?: entry.kind.label
}
