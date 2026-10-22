package com.kigtts.next.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A slow worker never loses the final slider position. Pending values coalesce per control. */
internal class PluginControlQueue(private val scope: CoroutineScope,
    private val send: suspend (String, String, Map<String, String>) -> Unit) {
    private val pending = linkedMapOf<Pair<String, String>, Map<String, String>>()
    private var job: Job? = null

    fun submit(plugin: String, action: String, inputs: Map<String, String>) {
        pending[plugin to action] = inputs
        if (job?.isActive == true) return
        job = scope.launch {
            while (pending.isNotEmpty()) {
                delay(50)
                val entry = pending.entries.first()
                val key = entry.key
                val value = pending.remove(key) ?: continue
                send(key.first, key.second, value)
            }
        }
    }
}
