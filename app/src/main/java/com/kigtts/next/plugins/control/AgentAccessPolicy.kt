package com.kigtts.next.plugins.control

import android.content.Context

/** Only native composer gestures set policy. Forwarded plugin inputs cannot mint approval. */
class AgentAccessPolicy(context: Context) {
    private val preferences = context.getSharedPreferences("agent-access", 0)
    private val approved = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun full(owner: String) = preferences.getBoolean(owner, false)
    fun consume(owner: String): Boolean = full(owner) || (approved.remove(owner) ?: 0L) > android.os.SystemClock.elapsedRealtime()
    fun userAction(owner: String, inputs: Map<String, String>) {
        if (inputs["_callerPlugin"] != null) return
        inputs["_agentAccess"]?.let { mode ->
            require(mode in setOf("ask", "full"))
            preferences.edit().putBoolean(owner, mode == "full").apply()
            approved.remove(owner)
        }
        if (inputs["_agentApprove"] == "true") approved[owner] = android.os.SystemClock.elapsedRealtime() + 30_000
    }
}
