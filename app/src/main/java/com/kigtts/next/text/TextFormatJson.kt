package com.kigtts.next.text

import org.json.JSONArray
import org.json.JSONObject

internal fun readTextFormats(array: JSONArray?, length: Int): List<TextFormatRun> = TextFormatting.normalize(
    (0 until minOf(array?.length() ?: 0, 256)).mapNotNull { index -> runCatching {
        val run = requireNotNull(array).getJSONObject(index)
        TextFormatRun(run.getInt("start"), run.getInt("end"), TextFormat.valueOf(run.getString("kind")), run.optLong("value"))
    }.getOrNull() }, length)

internal fun writeTextFormats(formats: List<TextFormatRun>, length: Int): JSONArray = JSONArray().also { array ->
    TextFormatting.normalize(formats, length).forEach { array.put(JSONObject().put("start", it.start)
        .put("end", it.end).put("kind", it.kind.name).put("value", it.value)) }
}
