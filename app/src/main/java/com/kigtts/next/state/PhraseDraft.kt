package com.kigtts.next.state

import com.kigtts.next.media.*
import com.kigtts.next.text.TextFormatRun

data class PhraseDraft(val key: String, val phraseId: String?, val text: String, val title: String,
    val category: String, val mediaId: String?, val type: QuickCardType = QuickCardType.TEXT,
    val colorMode: CardColorMode = CardColorMode.DEFAULT, val color: Long = 0xFF00696E,
    val richText: Boolean = true, val formats: List<TextFormatRun> = emptyList(),
    val resources: Map<QuickCardType, String?> = emptyMap(), val autoPlay: Boolean = true,
    val audioMode: MediaAudioMode? = null,val metadata:Map<String,String> = emptyMap()) {
    fun switchType(next: QuickCardType) = if (next == type) this else copy(type = next, mediaId = resources[next], resources = resources + (type to mediaId))
    fun attach(next: QuickCardType, id: String) = copy(mediaId = if (type == next) id else mediaId,
        resources = resources + (next to id))
    fun valid(repository: MediaRepository): Boolean = (type != QuickCardType.TEXT || text.isNotBlank()) && when (type) {
        QuickCardType.TEXT -> true
        QuickCardType.AUDIO -> repository.get(mediaId)?.kind == MediaKind.AUDIO
        QuickCardType.MEDIA -> repository.get(mediaId)?.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO, MediaKind.VIDEO)
        QuickCardType.PAGE -> repository.get(mediaId)?.kind in setOf(MediaKind.HTML, MediaKind.WEB)
    }
}
