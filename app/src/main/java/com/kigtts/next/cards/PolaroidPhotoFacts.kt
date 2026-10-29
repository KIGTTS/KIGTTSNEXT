package com.kigtts.next.cards

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object PolaroidPhotoFacts {
    fun read(file: File?, fromCamera: Boolean, now: Long = System.currentTimeMillis()): FrozenPhotoFacts {
        var facts = FrozenPhotoFacts(capturedAt = if (fromCamera) now else null, fromCamera = fromCamera)
        if (file == null) return facts
        return runCatching {
            val exif = ExifInterface(file)
            val local = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (local != null) runCatching {
                val date = LocalDateTime.parse(local, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT))
                val zone = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let(ZoneOffset::of) ?: ZoneId.systemDefault()
                facts = facts.copy(capturedAt = date.atZone(zone).toInstant().toEpochMilli(), zone = zone.id)
            }
            exif.latLong?.let { facts = facts.copy(latitude = it[0], longitude = it[1]) }
            facts
        }.getOrDefault(facts)
    }

    suspend fun enrich(context: Context, facts: FrozenPhotoFacts, fields: Set<PhotoTextField>,
        access: PhotoFactsAccess = AndroidPhotoFactsAccess(context), now: Long = System.currentTimeMillis()): FrozenPhotoFacts {
        if (fields.none { it == PhotoTextField.LOCATION || it == PhotoTextField.WEATHER }) return facts
        var result = facts
        // A location obtained now must never be presented as the location of an old photograph.
        val recent = facts.fromCamera && facts.capturedAt?.let { now - it in 0..10 * 60 * 1000L } == true
        val needsPlace = PhotoTextField.LOCATION in fields && result.location.isNullOrBlank()
        val needsWeather = PhotoTextField.WEATHER in fields && result.weather.isNullOrBlank()
        val hasCoordinates = validPhotoCoordinates(result.latitude, result.longitude)
        val locationAllowed = (needsPlace || needsWeather) && (hasCoordinates || recent) && access.consent("location")
        if (locationAllowed) {
            if (!hasCoordinates && access.locationPermission()) {
                PhotoFactsWork.bounded { access.currentLocation() }?.takeIf { validPhotoCoordinates(it.latitude, it.longitude) }
                    ?.let { result = result.copy(latitude = it.latitude, longitude = it.longitude) }
            }
        }
        val lat = result.latitude; val lon = result.longitude
        if (!locationAllowed || !validPhotoCoordinates(lat, lon)) return result
        requireNotNull(lat); requireNotNull(lon)
        val weatherAllowed = needsWeather && result.capturedAt != null && access.consent("weather")
        // Both lookups share the same time budget; a slow place name cannot delay weather.
        result = coroutineScope {
            val place = async { if (needsPlace) PhotoFactsWork.bounded { access.place(lat, lon) } else result.location }
            val weather = async { if (weatherAllowed) PhotoFactsWork.bounded { access.weather(result) } else result.weather }
            result.copy(location = place.await()?.takeIf(String::isNotBlank), weather = weather.await()?.takeIf(String::isNotBlank))
        }
        return result
    }

    /** An already enabled capture fact is saved even when the current paper preset does not show it. */
    suspend fun enrichCapture(context: Context, facts: FrozenPhotoFacts, fields: Set<PhotoTextField>,
        access: PhotoFactsAccess = AndroidPhotoFactsAccess(context), now: Long = System.currentTimeMillis()): FrozenPhotoFacts =
        enrich(context, facts, fields + PhotoFactsConsent.rememberedFields(context), access, now)
}

internal fun validPhotoCoordinates(latitude: Double?, longitude: Double?) =
    latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
