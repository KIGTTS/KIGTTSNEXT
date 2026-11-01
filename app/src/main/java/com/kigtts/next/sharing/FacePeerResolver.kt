package com.kigtts.next.sharing

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val PROFILE_LOG_TAG = "FaceTransfer"

/** Bounded profile probes retry after failure and never publish results from a closed discovery session. */
internal class FacePeerResolver(private val scope: CoroutineScope, private val owner: String,
    private val found: (FacePeer) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val request: ((FacePeer, String) -> JSONObject)? = null) {
    private val limit = Semaphore(8)
    private val probes = ConcurrentHashMap.newKeySet<String>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val attempts = ConcurrentHashMap<String, Long>()
    @Volatile private var generation = 0
    fun refresh(peer: FacePeer): Job? {
        if (facePeerHosts(peer).isEmpty()) return null
        val key = "${peer.id}/${peer.token}/${peer.port}"
        val now = clock()
        if (jobs.containsKey(key) || attempts[key]?.let { now - it < if (peer.profile == null) 3_000 else 15_000 } == true) return null
        attempts[key] = now
        val epoch = generation
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { resolve(peer)?.let { if (epoch == generation) found(it) } }
            finally { jobs.remove(key, coroutineContext[Job]) }
        }
        return if (jobs.putIfAbsent(key, job) == null) job.also { it.start() } else { job.cancel(); null }
    }
    suspend fun resolve(peer: FacePeer): FacePeer? = coroutineScope {
        val hosts = facePeerHosts(peer)
        val results = Channel<FacePeer?>(hosts.size.coerceAtLeast(1))
        val owners = hosts.map { "$owner/${faceToken()}" }
        val workers = hosts.mapIndexed { index, host -> launch(Dispatchers.IO) {
            val probe = owners[index]
            try { limit.withPermit {
                ensureActive(); probes += probe
                val result = try {
                    val json = request?.invoke(peer, host) ?: FaceTransferClient.request(host, peer.port, "GET",
                        "/s/${peer.token}/hello", owner = probe, timeoutMillis = 3_000)
                    facePeerProfile(peer.copy(host = host), json)
                }
                catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) {
                    // Never log capability URLs, tokens, or personal profile contents.
                    if (Log.isLoggable(PROFILE_LOG_TAG, Log.DEBUG))
                        Log.d(PROFILE_LOG_TAG, "Profile probe $host:${peer.port} failed (${failure.javaClass.simpleName})")
                    null
                }
                results.trySend(result)
            } } finally { probes -= probe; FaceTransferClient.cancel(probe) }
        } }
        try {
            repeat(hosts.size) { results.receive()?.let { return@coroutineScope it } }
            null
        } finally {
            workers.forEach { it.cancel() }; owners.forEach(FaceTransferClient::cancel); results.close()
        }
    }
    fun clear() {
        generation++; jobs.values.toList().forEach { it.cancel() }; jobs.clear(); attempts.clear()
        probes.toList().forEach(FaceTransferClient::cancel)
    }
}
