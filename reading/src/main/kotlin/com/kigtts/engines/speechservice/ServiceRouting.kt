// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A service routes standard lifecycle signals; the provider alone owns resident native state. */
internal class ServiceRouting(private val settings: ServiceSettings, private val broker: SpeechBroker,
    private val system: TtsEngine, private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private data class Route(val id: String?, val voice: String?, val generation: String = "")
    private val mutex = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private var route: Route? = null
    private var used = false
    private var idlePolicy: Pair<Boolean, Int>? = null
    private var idle: Job? = null
    private var idleGeneration = 0L
    private var inFlight = 0
    private var active = false
    @Volatile private var automatic = false
    @Volatile private var desiredPlayback: Boolean? = null
    init { scope.launch { for (ignored in changes) {
        try { mutex.withLock {
            desiredPlayback?.let { playback(it) }
            if (route != null) reconcile(false)
        } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { }
    } } }
    fun changed(automaticRead: Boolean? = null, speechActive: Boolean? = null) {
        if (automaticRead != null) automatic = automaticRead
        if (speechActive != null) desiredPlayback = speechActive
        changes.trySend(Unit)
    }
    suspend fun <T> use(block: suspend (TtsProvider?) -> T): T {
        val provider = mutex.withLock {
            cancelIdle()
            inFlight++
            try { reconcile(false).also { used = true } }
            catch (failure: Exception) { inFlight--; scheduleIdle(); throw failure }
        }
        return try { block(provider) } finally {
            withContext(NonCancellable) { mutex.withLock { inFlight--; scheduleIdle() } }
        }
    }
    suspend fun control(command: TtsControl) = mutex.withLock {
        when (command.operation) {
            TtsControlOperation.PREPARE -> { reconcile(true); used = true; scheduleIdle() }
            TtsControlOperation.RELEASE -> { active = false; release() }
            TtsControlOperation.SET_RESIDENT -> { settings.resident = command.resident; reconcile(false) }
            TtsControlOperation.ACTIVITY -> playback(command.active)
        }
    }
    private suspend fun playback(value: Boolean) {
        if (active == value) return
        active = value
        val command = TtsControl(TtsControlOperation.ACTIVITY, active = value)
        try {
            system.control(command)
            route?.takeIf { it.id != null }?.let { send(it, command) }
        } finally { scheduleIdle() }
    }
    private suspend fun reconcile(warm: Boolean): TtsProvider? {
        val provider = try { broker.providers().firstOrNull {
            it.pluginId == settings.provider && it.pluginId != LEGACY_SYSTEM_PROVIDER && it.pluginId != SYSTEM_PROVIDER &&
                it.enabled && it.info.availability == EngineAvailability.READY
        } } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        val next = Route(provider?.pluginId, if (provider == null) settings.systemVoice else settings.voice, provider?.generation.orEmpty())
        val previous = route
        val switched = previous != next
        val preload = warm || switched && (automatic || previous?.id == next.id && used)
        if (switched) {
            release()
            send(next, TtsControl(TtsControlOperation.SET_RESIDENT, resident = true))
            route = next
        }
        if (switched && active) send(next, TtsControl(TtsControlOperation.ACTIVITY, active = true))
        if (preload) { send(next, TtsControl(TtsControlOperation.PREPARE, next.voice)); used = true }
        val policy = settings.resident to settings.idleMinutes
        if (idlePolicy != policy) { idlePolicy = policy; scheduleIdle() }
        return provider
    }
    private suspend fun send(target: Route, command: TtsControl) {
        if (target.id == null) system.control(command)
        else if (broker.providers().firstOrNull { it.pluginId == target.id }?.info?.supportsControl == true)
            broker.control(target.id, command)
    }
    suspend fun fallback() = mutex.withLock {
        system.control(TtsControl(TtsControlOperation.SET_RESIDENT, resident = true))
        system.control(TtsControl(TtsControlOperation.ACTIVITY, active = active))
    }

    private fun cancelIdle() { idleGeneration++; idle?.cancel(); idle = null }
    private fun scheduleIdle() {
        cancelIdle()
        if (route == null || settings.resident || active || inFlight != 0) return
        val generation = idleGeneration
        idle = scope.launch {
            delay(settings.idleMinutes * 60_000L)
            mutex.withLock {
                if (generation == idleGeneration && inFlight == 0 && !active && !settings.resident) {
                    idle = null
                    release()
                }
            }
        }
    }
    private suspend fun release() {
        cancelIdle()
        val previous = route
        route = null; used = false; idlePolicy = null
        if (previous?.id != null) {
            // Restore the provider's own idle behavior when it leaves this service's lease.
            releaseControl { send(previous, TtsControl(TtsControlOperation.SET_RESIDENT, resident = false)) }
            releaseControl { send(previous, TtsControl(TtsControlOperation.RELEASE)) }
        }
        releaseControl { system.control(TtsControl(TtsControlOperation.SET_RESIDENT, resident = false)) }
        releaseControl { system.control(TtsControl(TtsControlOperation.RELEASE)) }
    }
    private suspend fun releaseControl(action: suspend () -> Unit) {
        try { action() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { }
    }
    suspend fun close() { scope.cancel(); mutex.withLock { release(); system.close() } }
}
