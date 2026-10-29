package com.kigtts.next.cards

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** A capture-hour model estimate, never today's weather substituted for an old photo. */
internal object PhotoWeatherSnapshot {
    fun request(facts: FrozenPhotoFacts, now: Long = System.currentTimeMillis()): String {
        require(validPhotoCoordinates(facts.latitude, facts.longitude))
        val at = requireNotNull(facts.capturedAt)
        require(at > 0 && at <= now + 10 * 60 * 1000)
        val date = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val host = if (date.isBefore(today.minusDays(5))) "https://archive-api.open-meteo.com/v1/archive" else "https://api.open-meteo.com/v1/forecast"
        // Only approximate coordinates leave the device, including after a precise GPS fix.
        val coordinates = "latitude=%.2f&longitude=%.2f".format(Locale.ROOT, facts.latitude, facts.longitude)
        return "$host?$coordinates&hourly=temperature_2m,weather_code&start_date=$date&end_date=$date&timezone=UTC&timeformat=unixtime"
    }

    suspend fun read(facts: FrozenPhotoFacts): String? {
        val url = runCatching { request(facts) }.getOrNull() ?: return null
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2000; connection.readTimeout = 2000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "KIGTTS/1.0 (photo weather; https://github.com/KIGTTS)")
        return try { PhotoFactsWork.network {
            check(connection.responseCode == 200)
            val bytes = connection.inputStream.use { input ->
                val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192)
                while (buffer.size() <= 512 * 1024) {
                    val count = input.read(chunk); if (count < 0) break
                    buffer.write(chunk, 0, count)
                }
                buffer.toByteArray()
            }
            require(bytes.size <= 512 * 1024)
            parse(facts, bytes.toString(Charsets.UTF_8))
        } } finally { connection.disconnect() }
    }

    fun parse(facts: FrozenPhotoFacts, json: String): String? {
        val hourly = JSONObject(json).optJSONObject("hourly") ?: return null
        val times = hourly.optJSONArray("time") ?: return null
        val hour = floor(requireNotNull(facts.capturedAt) / 3_600_000.0).toLong() * 3600
        val index = (0 until times.length()).firstOrNull { times.optLong(it, Long.MIN_VALUE) == hour } ?: return null
        val codes = hourly.optJSONArray("weather_code") ?: return null
        val temperatures = hourly.optJSONArray("temperature_2m") ?: return null
        if (codes.isNull(index) || temperatures.isNull(index)) return null
        val temperature = temperatures.optDouble(index, Double.NaN)
        if (!temperature.isFinite()) return null
        val description = when (codes.optInt(index, -1)) {
            0 -> "晴"; 1, 2 -> "少云"; 3 -> "阴"; 45, 48 -> "雾"
            in 51..67, in 80..82 -> "雨"; in 71..77, 85, 86 -> "雪"; in 95..99 -> "雷雨"
            else -> return null
        }
        return "$description ${temperature.roundToInt()}°C"
    }
}
