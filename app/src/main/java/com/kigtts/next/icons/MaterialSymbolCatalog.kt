package com.kigtts.next.icons

import android.content.Context

/** Shared offline name validation for UI selection, storage and plugin transactions. */
object MaterialSymbolCatalog {
    @Volatile private var context: Context? = null
    fun initialize(value: Context) { context = value.applicationContext }
    private val glyphs: Map<String, String> by lazy {
        requireNotNull(context).assets.open("icons/material-symbols.codepoints").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim().split(' ') }.filter { it.size == 2 }.associate { it[0] to String(Character.toChars(it[1].toInt(16))) }
        }
    }
    fun contains(name: String) = context != null && name.matches(Regex("[a-z0-9_]{1,80}")) && name in glyphs
    fun glyph(name: String) = if (context == null) null else glyphs[name]
    fun search(query: String, offset: Int = 0, limit: Int = 80): List<String> = glyphs.keys.filter { it.contains(query.trim().lowercase()) }.sorted().drop(offset.coerceAtLeast(0)).take(limit.coerceIn(0, 100))
}
