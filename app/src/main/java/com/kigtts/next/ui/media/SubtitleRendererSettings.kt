package com.kigtts.next.ui.media

import com.kigtts.next.io.readUpTo
import com.kigtts.next.media.WebContentPolicy
import java.io.File
import org.json.JSONObject

/** Optional renderer-owned presentation data. Never reads beyond the selected resource root. */
internal object SubtitleRendererSettings {
    const val FILE = "renderer.json"
    fun read(root: File): String = runCatching {
        val file = WebContentPolicy.file(root, FILE) ?: return "{}"
        file.inputStream().use { input ->
            val bytes = input.readUpTo(4097)
            if (bytes.size > 4096) "{}" else JSONObject(bytes.toString(Charsets.UTF_8)).toString()
        }
    }.getOrDefault("{}")
}
