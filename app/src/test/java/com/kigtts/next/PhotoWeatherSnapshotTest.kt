package com.kigtts.next

import com.kigtts.next.cards.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PhotoWeatherSnapshotTest {
    private val now = Instant.parse("2026-10-29T00:10:00Z").toEpochMilli()
    @Test fun captureHourUsesUnixTimeAcrossMidnightAndIgnoresDisplayTimezone() {
        val facts = FrozenPhotoFacts(now, "Pacific/Honolulu", 31.234567, 121.478912)
        val request = PhotoWeatherSnapshot.request(facts, now)
        assertTrue(request.startsWith("https://api.open-meteo.com/v1/forecast?"))
        assertTrue(request.contains("start_date=2026-10-29&end_date=2026-10-29"))
        assertTrue(request.contains("latitude=31.23&longitude=121.48"))
        assertTrue(request.contains("timeformat=unixtime")); assertFalse(request.contains("31.234567"))
        val hour = now / 3_600_000 * 3600
        val json = """{"hourly":{"time":[$hour],"weather_code":[0],"temperature_2m":[-2.7]}}"""
        assertEquals("晴 -3°C", PhotoWeatherSnapshot.parse(facts,json))
    }
    @Test fun archiveDatesAreCaptureDatesAndMissingValuesAreNeverInvented() {
        val facts = FrozenPhotoFacts(now-30L*24*3600*1000, latitude=31.23, longitude=121.47)
        assertTrue(PhotoWeatherSnapshot.request(facts,now).startsWith("https://archive-api.open-meteo.com/v1/archive?"))
        val hour = facts.capturedAt!! / 3_600_000 * 3600
        for (code in listOf("null","1000")) {
            assertNull(PhotoWeatherSnapshot.parse(facts,"""{"hourly":{"time":[$hour],"weather_code":[$code],"temperature_2m":[18]}}"""))
        }
        assertNull(PhotoWeatherSnapshot.parse(facts,"""{"hourly":{"time":[$hour],"weather_code":[0],"temperature_2m":[null]}}"""))
        assertNull(PhotoWeatherSnapshot.parse(facts,"""{"hourly":{"time":[0],"weather_code":[0],"temperature_2m":[20]}}"""))
        assertThrows(IllegalArgumentException::class.java) { PhotoWeatherSnapshot.request(facts.copy(latitude=Double.NaN),now) }
        assertThrows(IllegalArgumentException::class.java) { PhotoWeatherSnapshot.request(facts.copy(capturedAt=now+24*3600*1000),now) }
    }
}
