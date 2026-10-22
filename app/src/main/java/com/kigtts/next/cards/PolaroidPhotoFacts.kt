package com.kigtts.next.cards

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.exifinterface.media.ExifInterface
import com.kigtts.next.media.WebAndroidPermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

/** Every optional disclosure has a native confirmation, independent of Agent access mode. */
internal object PhotoFactsConsent {
    data class Request(val kind: String, val result: CompletableDeferred<Boolean> = CompletableDeferred())
    val pending = MutableStateFlow<Request?>(null)
    private val mutex = Mutex()
    suspend fun request(context: Context, kind: String): Boolean = mutex.withLock {
        val prefs = context.getSharedPreferences("polaroid-facts", 0)
        if (prefs.getBoolean(kind, false)) return@withLock true
        val request = Request(kind); pending.value = request
        try {
            val allowed = withTimeoutOrNull(90000) { request.result.await() } == true
            if (allowed) prefs.edit().putBoolean(kind, true).apply()
            allowed
        } finally { if (pending.value === request) pending.value = null }
    }
}

internal object PolaroidPhotoFacts {
    fun read(file: File?, fromCamera: Boolean, now: Long = System.currentTimeMillis()): FrozenPhotoFacts {
        var facts = FrozenPhotoFacts(capturedAt = if (fromCamera) now else null)
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
        val recent = facts.capturedAt?.let { now - it in 0..10 * 60 * 1000L } == true
        if ((result.latitude != null || recent) && result.location == null && access.consent("location")) {
            if (result.latitude == null && access.locationPermission()) {
                access.currentLocation()?.let { result = result.copy(latitude = it.latitude, longitude = it.longitude) }
            }
            val lat = result.latitude; val lon = result.longitude
            if (lat != null && lon != null) {
                val place = access.place(lat, lon)
                result = result.copy(location = place ?: "约 %.2f, %.2f".format(Locale.ROOT, lat, lon))
            }
        }
        if (PhotoTextField.WEATHER in fields && result.weather == null && result.capturedAt != null && result.latitude != null && result.longitude != null && access.consent("weather")) {
            val weather = access.weather(result)
            result = result.copy(weather = weather)
        }
        return result
    }

}

/** Open-Meteo model estimate for the capture hour, stored once rather than refreshed on display. */
internal object PhotoWeatherSnapshot {
    fun read(facts: FrozenPhotoFacts): String {
        val instant = Instant.ofEpochMilli(requireNotNull(facts.capturedAt)).atZone(ZoneId.of(facts.zone))
        val date = instant.withZoneSameInstant(ZoneOffset.UTC).toLocalDate()
        require(!date.isAfter(LocalDate.now(ZoneId.of(facts.zone))))
        val old = date.isBefore(LocalDate.now().minusDays(5))
        val host = if (old) "https://archive-api.open-meteo.com/v1/archive" else "https://api.open-meteo.com/v1/forecast"
        // Round the disclosure to an approximate location; never transmit photo contents or device identifiers.
        val coordinates = "latitude=%.2f&longitude=%.2f".format(Locale.ROOT, requireNotNull(facts.latitude), requireNotNull(facts.longitude))
        val connection = URL("$host?$coordinates&hourly=temperature_2m,weather_code&start_date=$date&end_date=$date&timezone=UTC").openConnection().apply { connectTimeout = 4000; readTimeout = 4000 }
        val bytes = connection.getInputStream().use { input ->
            val buffer = java.io.ByteArrayOutputStream(); val part = ByteArray(8192)
            while (buffer.size() <= 512 * 1024) { val count = input.read(part); if (count < 0) break; buffer.write(part, 0, count) }; buffer.toByteArray()
        }
        require(bytes.size <= 512 * 1024)
        val hourly = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val utcHour = instant.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().withMinute(0).withSecond(0).withNano(0).toString()
        val index = (0 until times.length()).firstOrNull { times.getString(it) == utcHour } ?: error("未提供拍摄时段天气")
        val code = hourly.getJSONArray("weather_code").getInt(index)
        val description = when (code) { 0 -> "晴"; 1, 2 -> "少云"; 3 -> "阴"; 45, 48 -> "雾"; in 51..67, in 80..82 -> "雨"; in 71..77, 85, 86 -> "雪"; in 95..99 -> "雷雨"; else -> "天气" }
        return "$description ${hourly.getJSONArray("temperature_2m").getDouble(index).toInt()}°C · 模型估计"
    }
}
