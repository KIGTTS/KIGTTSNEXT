package com.kigtts.next.legacy

import org.json.JSONObject

/** JSONObject itself has no nesting budget, including JSON embedded in old preference strings. */
internal fun legacyJson(text: String): JSONObject {
    require(text.length <= LegacyArchive.MAX_METADATA) { "备份描述过大" }
    var depth = 0; var quoted = false; var escaped = false
    text.forEach { character ->
        if (quoted) {
            if (escaped) escaped = false else when (character) { '\\' -> escaped = true; '"' -> quoted = false }
        } else when (character) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 32) { "备份描述层级过深" } }
            '}', ']' -> { depth--; require(depth >= 0) { "备份描述格式不正确" } }
        }
    }
    require(!quoted && depth == 0) { "备份描述不完整" }
    return JSONObject(text)
}
