package com.kigtts.next.cards

import com.kigtts.plugin.api.PolaroidCard
import org.json.JSONObject

/** Native catalog metadata, kept outside the stable plugin card constructor. Unknown old dates stay 0. */
internal class PolaroidCreationTimes {
    private var values: Map<String, Long> = emptyMap()
    fun load(json: JSONObject?, cards: List<PolaroidCard>) {
        values = cards.associate { it.id to (json?.optLong(it.id, 0L) ?: 0L).coerceAtLeast(0L) }
    }
    fun get(id: String): Long = values[id] ?: 0L
    fun plan(before: List<PolaroidCard>, after: List<PolaroidCard>, now: Long = System.currentTimeMillis()): Map<String, Long> {
        val existing = before.map { it.id }.toSet()
        return after.associate { it.id to (values[it.id] ?: if (it.id in existing) 0L else now.coerceAtLeast(0L)) }
    }
    fun commit(next: Map<String, Long>) { values = next }
    fun json(next: Map<String, Long>): JSONObject = JSONObject().also { json -> next.forEach { (id, time) -> json.put(id, time) } }
}
