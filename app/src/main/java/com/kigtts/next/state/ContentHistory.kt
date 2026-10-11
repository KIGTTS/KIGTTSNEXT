package com.kigtts.next.state

import androidx.compose.ui.text.input.TextFieldValue
import com.kigtts.next.text.TextFormatRun

/** Presentation history belongs to the subtitle card, independently of page navigation. */
internal class ContentHistory {
    data class Entry(val text: TextFieldValue, val formats: List<TextFormatRun>, val media: String?, val audio: String?,
        val autoPlay: Boolean, val fullscreen: Boolean, val audioMode: com.kigtts.next.media.MediaAudioMode?)
    private val entries = ArrayDeque<Entry>()
    fun push(state: AppState) {
        entries.addLast(Entry(state.editorValue, state.subtitleFormats, state.mediaId, state.audioId,
            state.contentAutoPlay, state.forcedFullscreen, state.contentAudioMode))
        if (entries.size > 16) entries.removeFirst()
    }
    fun pop(): Entry? = if (entries.isEmpty()) null else entries.removeLast()
    fun clear() = entries.clear()
    fun remove(id: String) { entries.removeAll { it.media == id || it.audio == id } }
}
