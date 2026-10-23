package com.kigtts.next.cards

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.kigtts.next.media.WebAndroidPermissions
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

internal interface PhotoFactsAccess {
    suspend fun consent(kind: String): Boolean
    suspend fun locationPermission(): Boolean
    suspend fun currentLocation(): Location?
    suspend fun place(latitude: Double, longitude: Double): String?
    suspend fun weather(facts: FrozenPhotoFacts): String?
}

internal class AndroidPhotoFactsAccess(private val context: Context) : PhotoFactsAccess {
    override suspend fun consent(kind: String) = PhotoFactsConsent.request(context, kind)
    override suspend fun locationPermission() = WebAndroidPermissions.request(context, listOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    @Suppress("MissingPermission")
    override suspend fun currentLocation(): Location? = withTimeoutOrNull(PhotoFactsWork.TIMEOUT_MS) {
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withTimeoutOrNull null
        val provider = LocationManager.NETWORK_PROVIDER
        // Use the phone's own location service, including Chinese ROM implementations.
        manager.getLastKnownLocation(provider)?.takeIf { System.currentTimeMillis() - it.time in 0..120000 }?.let {
            return@withTimeoutOrNull it
        }
        if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) return@withTimeoutOrNull null
        if (android.os.Build.VERSION.SDK_INT >= 30) suspendCancellableCoroutine { continuation ->
            val cancel = android.os.CancellationSignal(); continuation.invokeOnCancellation { cancel.cancel() }
            manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location -> if (continuation.isActive) continuation.resume(location) }
        } else manager.getLastKnownLocation(provider)?.takeIf { System.currentTimeMillis() - it.time < 120000 }
    }
    override suspend fun place(latitude: Double, longitude: Double): String? = PhotoFactsWork.run {
        if (!Geocoder.isPresent()) return@run null
        @Suppress("DEPRECATION")
        runCatching { Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let {
            listOfNotNull(it.adminArea, it.locality, it.subLocality).distinct().joinToString(" · ").takeIf(String::isNotBlank)
        } }.getOrNull()
    }
    override suspend fun weather(facts: FrozenPhotoFacts): String? = PhotoFactsWork.run {
        runCatching { PhotoWeatherSnapshot.read(facts) }.getOrNull()
    }
}
