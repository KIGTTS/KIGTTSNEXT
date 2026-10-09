package com.kigtts.next.manager

import org.json.JSONArray
import org.json.JSONObject

/** Bounded process-local diagnostics; no plugin model, transcript or secret is recorded. */
class AdminJournal {
    private val entries = ArrayDeque<JSONObject>()
    @Synchronized fun record(command: String, id: String, success: Boolean, detail: String) {
        entries.addLast(JSONObject().put("time",System.currentTimeMillis()).put("command",command).put("id",id)
            .put("ok",success).put("detail",detail.take(1024)))
        while(entries.size>200)entries.removeFirst()
    }
    @Synchronized fun snapshot() = JSONArray(entries.toList())
}
