package com.kigtts.next.cards

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Basic, anonymous UAPI city observations. Forecast/pro endpoints are not requested. */
internal object PhotoUapiWeather {
    private val relative = Regex("(\\d{1,3})\\s*(秒|分钟|小时)前(?:发布|更新)?")
    private val localTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun read(city: String?, capturedAt: Long, now: Long, fetch: PhotoJsonFetch): String? {
        if (!PhotoWeatherSources.matchesCapture(now, capturedAt, now)) return null
        val name = city?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 } ?: return null
        val response = fetch(PhotoServiceHttp.query("https://uapis.cn/api/v1/misc/weather",
            "city" to name, "lang" to "zh")) ?: return null
        return parse(response, capturedAt, now)
    }

    fun parse(json: JSONObject, capturedAt: Long, now: Long): String? {
        if (json.optString("city").isBlank()) return null
        val report = json.optString("report_time").trim().takeIf { it.length in 1..64 } ?: return null
        val observed = when {
            report in setOf("刚刚", "刚刚发布", "刚刚更新") -> now
            relative.matches(report) -> relative.matchEntire(report)!!.let { match ->
                val unit = when (match.groupValues[2]) { "秒" -> 1000L; "分钟" -> 60_000L; else -> 3600_000L }
                now - match.groupValues[1].toLong() * unit
            }
            else -> runCatching { OffsetDateTime.parse(report).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(report, localTime).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() }.getOrNull()
        }
        if (!PhotoWeatherSources.matchesCapture(observed, capturedAt, now)) return null
        return PhotoWeatherSources.caption(json.optString("weather"), json.optString("temperature").toDoubleOrNull())
    }
}
