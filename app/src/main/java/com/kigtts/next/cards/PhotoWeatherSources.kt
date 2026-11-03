package com.kigtts.next.cards

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal object PhotoWeatherSources {
    private const val CURRENT_WINDOW = 3 * 60 * 60 * 1000L
    suspend fun read(settings: PhotoServiceConfig, facts: FrozenPhotoFacts,
        now: Long = System.currentTimeMillis(), city: String? = null, fetch: PhotoJsonFetch = PhotoServiceHttp::get): String? {
        if (!validPhotoCoordinates(facts.latitude, facts.longitude)) return null
        if (settings.weatherSource == PhotoWeatherSource.OPEN_METEO) return PhotoWeatherSnapshot.read(facts)
        val at = facts.capturedAt ?: return null
        // Current-only sources must not silently supply today's weather for an old photo.
        if (now - at !in -600_000L..CURRENT_WINDOW) return null
        val source = settings.weatherSource
        val key = settings.key(source)
        if (source.requiresKey && key.isBlank()) return null
        return when (source) {
            PhotoWeatherSource.UAPI -> PhotoUapiWeather.read(city, at, now, fetch)
            PhotoWeatherSource.AMAP -> {
                val city = PhotoAmapLocation.read(facts.latitude!!, facts.longitude!!, key, fetch)?.adcode ?: return null
                fetch(PhotoServiceHttp.query("https://restapi.amap.com/v3/weather/weatherInfo", "key" to key,
                    "city" to city, "extensions" to "base"))?.let { parseAmap(it, at, now) }
            }
            PhotoWeatherSource.SENIVERSE -> {
                val coordinates = "%.2f:%.2f".format(Locale.ROOT, facts.latitude, facts.longitude)
                fetch(PhotoServiceHttp.query("https://api.seniverse.com/v3/weather/now.json", "key" to key,
                    "location" to coordinates, "language" to "zh-Hans", "unit" to "c"))?.let { parseSeniverse(it, at, now) }
            }
            PhotoWeatherSource.OPEN_METEO -> null
        }
    }
    fun parseAmap(json: JSONObject, capturedAt: Long, now: Long): String? {
        if (json.optString("status") != "1") return null
        val live = json.optJSONArray("lives")?.optJSONObject(0) ?: return null
        val time = runCatching { LocalDateTime.parse(live.optString("reporttime"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() }.getOrNull()
        if (!matchesCapture(time, capturedAt, now)) return null
        return caption(live.optString("weather"), live.optString("temperature").toDoubleOrNull())
    }
    fun parseSeniverse(json: JSONObject, capturedAt: Long, now: Long): String? {
        val result = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        val time = runCatching { OffsetDateTime.parse(result.optString("last_update")).toInstant().toEpochMilli() }.getOrNull()
        if (!matchesCapture(time, capturedAt, now)) return null
        val weather = result.optJSONObject("now") ?: return null
        return caption(weather.optString("text"), weather.optString("temperature").toDoubleOrNull())
    }
    internal fun matchesCapture(observed: Long?, captured: Long, now: Long): Boolean = observed != null &&
        captured > 0 && now - captured in -600_000L..CURRENT_WINDOW && now - observed in -600_000L..CURRENT_WINDOW &&
        abs(observed - captured) <= CURRENT_WINDOW
    internal fun caption(text: String, temperature: Double?): String? {
        if (text.isBlank() || text == "null" || text.length > 50 || temperature == null || !temperature.isFinite() || temperature !in -100.0..70.0) return null
        return "$text ${temperature.roundToInt()}°C"
    }
}
