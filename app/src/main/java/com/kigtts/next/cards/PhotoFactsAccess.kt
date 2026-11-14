package com.kigtts.next.cards

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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

internal class AndroidPhotoFactsAccess(private val context: Context, private val interactive: Boolean = true) : PhotoFactsAccess {
    private val settings = PhotoServiceSettings(context).snapshot()
    override suspend fun consent(kind: String) = if (interactive) PhotoFactsConsent.request(context, kind, settings)
        else PhotoFactsConsent.granted(context, kind, settings)
    override suspend fun locationPermission(): Boolean {
        if (!consent("location")) return false
        fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!interactive) return has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
        val prefs = context.getSharedPreferences("polaroid-facts", 0)
        if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION) && prefs.getBoolean("location-permission-v2", false)) return true
        prefs.edit().putBoolean("location-permission-v2", true).apply()
        WebAndroidPermissions.request(context, listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        // Approximate permission remains sufficient; a fine-location denial must not disable the feature.
        return has(Manifest.permission.ACCESS_COARSE_LOCATION) || has(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    override suspend fun currentLocation() = PhotoCaptureLocation(context).current()
    override suspend fun place(latitude: Double, longitude: Double): String? {
        if (settings.placeSource == PhotoPlaceSource.AMAP) {
            return PhotoAmapLocation.read(latitude, longitude, settings.amapKey)?.name
        }
        if (settings.placeSource == PhotoPlaceSource.OFFLINE) {
            return withContext(Dispatchers.IO) { PhotoPlaceIndex.nearby(context, latitude, longitude) }
        }
        val address = try { if (!Geocoder.isPresent()) null else if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(2000) { suspendCancellableCoroutine<android.location.Address?> { continuation ->
                Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                })
            } }
        } else PhotoFactsWork.run(2000) {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
        } } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        return address?.let {
            listOfNotNull(it.adminArea, it.locality, it.subLocality).filter(String::isNotBlank).distinct().joinToString(" · ").takeIf(String::isNotBlank)
        } ?: withContext(Dispatchers.IO) { PhotoPlaceIndex.nearby(context, latitude, longitude) }
    }
    override suspend fun weather(facts: FrozenPhotoFacts): String? {
        val city = if (settings.weatherSource == PhotoWeatherSource.UAPI && validPhotoCoordinates(facts.latitude, facts.longitude)) {
            withContext(Dispatchers.IO) { PhotoPlaceIndex.city(context, facts.latitude!!, facts.longitude!!) }
        } else null
        return PhotoWeatherSources.read(settings, facts, city = city)
    }
}
