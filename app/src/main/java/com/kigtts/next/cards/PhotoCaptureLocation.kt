package com.kigtts.next.cards

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume

internal fun photoLocationIsFresh(location: Location, now: Long = System.currentTimeMillis(),
    elapsedNanos: Long = SystemClock.elapsedRealtimeNanos()): Boolean {
    if (!validPhotoCoordinates(location.latitude, location.longitude)) return false
    val age = if (location.elapsedRealtimeNanos > 0) (elapsedNanos - location.elapsedRealtimeNanos) / 1_000_000 else now - location.time
    return age in 0..120_000
}

/** Race the ROM's own providers; a missing network fix must not hide a valid GPS/fused fix. */
internal class PhotoCaptureLocation(private val context: Context) {
    @Suppress("MissingPermission")
    suspend fun current(): Location? = withTimeoutOrNull(PhotoFactsWork.TIMEOUT_MS) {
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withTimeoutOrNull null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return@withTimeoutOrNull null
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it in manager.allProviders && (it != LocationManager.GPS_PROVIDER || fine) }
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { photoLocationIsFresh(it) }.maxByOrNull { it.time }?.let { return@withTimeoutOrNull it }
        val enabled = providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (enabled.isEmpty()) return@withTimeoutOrNull null
        coroutineScope {
            val results = Channel<Location?>(Channel.UNLIMITED)
            val requests = enabled.map { provider -> launch {
                val location = try { request(manager, provider) } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { null }
                results.send(location?.takeIf { photoLocationIsFresh(it) })
            } }
            try {
                repeat(enabled.size) { results.receive()?.let { return@coroutineScope it } }
                null
            } finally { requests.forEach { it.cancel() }; results.cancel() }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun request(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }
}
