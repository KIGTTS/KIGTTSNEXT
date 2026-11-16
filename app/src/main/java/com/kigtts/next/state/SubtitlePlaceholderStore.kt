package com.kigtts.next.state

import android.content.Context
import androidx.core.content.edit

object SubtitlePlaceholder {
    const val DEFAULT = "点击输入文字"
    const val MAX_LENGTH = 200
    /** Keep user spaces/newlines, omit NUL and never split a surrogate pair at the limit. */
    fun normalize(value: String): String {
        val text = value.replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n')
        val end = text.offsetByCodePoints(0, minOf(text.codePointCount(0, text.length), MAX_LENGTH))
        return text.substring(0, end)
    }
}

/** A display preference only: it is never copied into the editable or spoken text. */
class SubtitlePlaceholderStore(context: Context, private val defaultPlaceholder: String = SubtitlePlaceholder.DEFAULT) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun load(default: String = defaultPlaceholder): String =
        SubtitlePlaceholder.normalize(preferences.getString("subtitlePlaceholder", null) ?: default)
    fun save(value: String) { preferences.edit { putString("subtitlePlaceholder", SubtitlePlaceholder.normalize(value)) } }
}
