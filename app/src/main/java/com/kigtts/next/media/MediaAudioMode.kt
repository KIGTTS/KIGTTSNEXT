package com.kigtts.next.media

/** Sound policy is independent of whether the moving picture starts automatically. */
enum class MediaAudioMode(val label: String) {
    SILENT("不播放"), AUTOMATIC("自动播放"), FOLLOW_READING("跟随朗读设置");

    fun audible(automaticReading: Boolean) = when (this) {
        SILENT -> false
        AUTOMATIC -> true
        FOLLOW_READING -> automaticReading
    }
    companion object {
        fun defaultFor(kind: MediaKind) = if (kind == MediaKind.VIDEO) AUTOMATIC else SILENT
        fun read(value: String?) = entries.firstOrNull { it.name == value }
    }
}
