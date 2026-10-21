package com.kigtts.next.plugins.control

import android.content.Context

/** Only native composer gestures set policy. Forwarded plugin inputs cannot mint approval. */
class AgentAccessPolicy(context: Context, private val identity: (String) -> String = { it }) {
    private val preferences = context.getSharedPreferences("agent-access", 0)
    private val approved = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val delegations = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    fun warningShown(owner: String) = preferences.getBoolean(identity(owner) + ":full-warned", false)
    fun full(owner: String) = warningShown(owner) && preferences.getBoolean(identity(owner), false)
    fun delegate(owner: String, runtime: String, project: String): java.io.Closeable? {
        if (!full(owner) || !project.matches(Regex("[a-z][a-z0-9.-]{1,79}"))) return null
        val key = "$runtime/$project"; val value = owner to (android.os.SystemClock.elapsedRealtime() + 60_000)
        delegations[key] = value
        return java.io.Closeable { delegations.remove(key, value) }
    }
    fun delegated(runtime: String, project: String): Boolean {
        val grant = delegations["$runtime/$project"] ?: return false
        return grant.second > android.os.SystemClock.elapsedRealtime() && full(grant.first)
    }
    fun consume(owner: String): Boolean = full(owner) || (approved.remove(owner) ?: 0L) > android.os.SystemClock.elapsedRealtime()
    fun userAction(owner: String, inputs: Map<String, String>) {
        if (inputs["_callerPlugin"] != null) return
        inputs["_agentAccess"]?.let { mode ->
            require(mode in setOf("ask", "assist", "full"))
            if (mode == "full" && !warningShown(owner)) require(inputs["_agentFullConfirmed"] == "true") { "请先确认完全访问警告" }
            preferences.edit().putBoolean(identity(owner), mode == "full").also { edit ->
                if (mode == "full") edit.putBoolean(identity(owner) + ":full-warned", true)
            }.apply()
            approved.remove(owner)
        }
        if (inputs["_agentApprove"] == "true") approved[owner] = android.os.SystemClock.elapsedRealtime() + 30_000
    }
}

val LocalAgentAccessPolicy = androidx.compose.runtime.staticCompositionLocalOf<AgentAccessPolicy?> { null }
