package com.kigtts.next.sharing

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import java.net.Inet4Address

internal fun faceNsdKey(info: NsdServiceInfo) = info.serviceName +
    if (Build.VERSION.SDK_INT >= 33) "@${info.network}" else ""

@Suppress("DEPRECATION")
internal fun faceNsdPeer(info: NsdServiceInfo, self: String): FacePeer? {
    fun attr(key: String) = info.attributes[key]?.toString(Charsets.UTF_8).orEmpty()
    val id = attr("id"); val token = attr("key")
    if (id == self || !id.matches(FACE_LAN_TOKEN) || !token.matches(FACE_LAN_TOKEN) || info.port !in 1..65535) return null
    val addresses = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
    val host = addresses.filter { it.isSiteLocalAddress || it.isLinkLocalAddress }
        .sortedBy { if (it is Inet4Address) 0 else 1 }.firstOrNull()?.hostAddress ?: return null
    return FacePeer(id, attr("name").take(40), host, info.port, token)
}

/** Older NSD stacks only allow one resolve at a time. Failed entries are retried without blocking others. */
@Suppress("DEPRECATION")
internal class FaceNsdResolver(private val manager: NsdManager, private val handler: Handler,
    private val found: (NsdServiceInfo) -> Unit) {
    private data class Pending(val info: NsdServiceInfo, var attempts: Int = 0)
    private val known = linkedMapOf<String, Pending>()
    private val queue = ArrayDeque<String>()
    private var active: NsdManager.ResolveListener? = null
    private var timeout: Runnable? = null
    private var epoch = 0
    private val retryTasks = mutableMapOf<String, Runnable>()

    fun add(info: NsdServiceInfo) {
        val key = faceNsdKey(info)
        if (key in known || known.size >= 64) return
        known[key] = Pending(info); queue.addLast(key); next()
    }
    fun remove(key: String) {
        known.remove(key); queue.remove(key)
        retryTasks.remove(key)?.let(handler::removeCallbacks)
    }
    private fun next() {
        if (active != null) return
        val key = queue.removeFirstOrNull() ?: return
        val pending = known[key] ?: return next()
        val generation = epoch
        pending.attempts++
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = complete(null)
            override fun onServiceResolved(info: NsdServiceInfo) = complete(info)
            fun complete(info: NsdServiceInfo?) { handler.post {
                if (generation != epoch || active !== this) return@post
                timeout?.let(handler::removeCallbacks); timeout = null
                active = null
                if (known[key] === pending) {
                    if (info != null && faceNsdPeer(info, "") != null) found(info)
                    else {
                        val task = Runnable { retryTasks.remove(key); if (known[key] === pending) { queue.addLast(key); next() } }
                        retryTasks[key] = task
                        handler.postDelayed(task, minOf(15_000L, 1_000L * pending.attempts))
                    }
                }
                next()
            } }
        }
        active = listener
        timeout = Runnable {
            if (active === listener) {
                if (Build.VERSION.SDK_INT >= 34) runCatching { manager.stopServiceResolution(listener) }
                listener.complete(null)
            }
        }.also { handler.postDelayed(it, 8_000) }
        runCatching { manager.resolveService(pending.info, listener) }.onFailure { listener.complete(null) }
    }
    fun clear() {
        epoch++; known.clear(); queue.clear()
        timeout?.let(handler::removeCallbacks); timeout = null
        retryTasks.values.forEach(handler::removeCallbacks); retryTasks.clear()
        active?.let { if (Build.VERSION.SDK_INT >= 34) runCatching { manager.stopServiceResolution(it) } }
        active = null
    }
}
