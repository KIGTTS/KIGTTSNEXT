package com.kigtts.next.state

import com.kigtts.next.media.MediaKind
import com.kigtts.next.text.TextFormatRun

enum class QuickCardType(val label: String) { TEXT("纯文本"), AUDIO("音频"), MEDIA("媒体"), PAGE("页面") }
enum class CardColorMode { DEFAULT, CUSTOM, NONE }

data class QuickPhrase(val text: String, val category: String, val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "", val mediaId: String? = null, val type: QuickCardType? = null,
    val colorMode: CardColorMode = CardColorMode.DEFAULT, val color: Long = 0xFF00696E,
    val richText: Boolean = false, val formats: List<TextFormatRun> = emptyList(), val autoPlay: Boolean = true) {
    fun cardType(mediaKind: MediaKind? = null): QuickCardType = type ?: when (mediaKind) {
        MediaKind.HTML, MediaKind.WEB -> QuickCardType.PAGE
        MediaKind.AUDIO -> QuickCardType.AUDIO
        else -> if (mediaId == null) QuickCardType.TEXT else QuickCardType.MEDIA
    }
}
