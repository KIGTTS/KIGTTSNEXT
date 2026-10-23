package com.kigtts.next.cards

import kotlinx.coroutines.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** A stalled ROM geocoder or DNS lookup must not hold the capture/editor coroutine open. */
internal object PhotoFactsWork {
    const val TIMEOUT_MS = 2500L
    private val workers = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue(),
        ThreadFactory { task -> Thread(task, "photo-facts").apply { isDaemon = true } })

    suspend fun <T> run(timeout: Long = TIMEOUT_MS, block: () -> T?): T? = withTimeoutOrNull(timeout) {
        suspendCancellableCoroutine { continuation ->
            try {
                val future = workers.submit {
                    val value = try { block() } catch (_: Exception) { null }
                    if (continuation.isActive) continuation.resume(value)
                }
                continuation.invokeOnCancellation { future.cancel(true) }
            } catch (_: RejectedExecutionException) { continuation.resume(null) }
        }
    }

    suspend fun <T> bounded(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() }
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { null }
}
