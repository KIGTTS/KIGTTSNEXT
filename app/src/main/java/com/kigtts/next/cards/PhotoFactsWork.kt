package com.kigtts.next.cards

import kotlinx.coroutines.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** A stalled ROM geocoder or DNS lookup must not hold the capture/editor coroutine open. */
internal object PhotoFactsWork {
    const val TIMEOUT_MS = 5000L
    private fun workers(name: String) = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8),
        ThreadFactory { task -> Thread(task, name).apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
    private val workers = workers("photo-place")
    private val networkWorkers = workers("photo-weather")

    suspend fun <T> run(timeout: Long = TIMEOUT_MS, block: () -> T?): T? = runOn(workers, timeout, block)
    suspend fun <T> network(timeout: Long = TIMEOUT_MS, block: () -> T?): T? = runOn(networkWorkers, timeout, block)
    private suspend fun <T> runOn(pool: ThreadPoolExecutor, timeout: Long, block: () -> T?): T? = withTimeoutOrNull(timeout) {
        suspendCancellableCoroutine { continuation ->
            try {
                val future = pool.submit {
                    val value = try { block() } catch (_: Exception) { null }
                    if (continuation.isActive) continuation.resume(value)
                }
                continuation.invokeOnCancellation {
                    future.cancel(true)
                    if (future is Runnable) pool.remove(future)
                }
            } catch (_: RejectedExecutionException) { continuation.resume(null) }
        }
    }

    suspend fun <T> bounded(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() }
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { null }
}
