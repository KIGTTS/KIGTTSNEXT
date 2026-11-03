package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PhotoWeatherSourcesTest {
    private val now = Instant.parse("2026-11-02T04:00:00Z").toEpochMilli()
    private val facts get() = FrozenPhotoFacts(now, latitude = 31.2345, longitude = 121.4789, fromCamera = true)

    @Test fun amapConvertsGpsThenUsesTheReturnedDistrictForWeather() = runTest {
        val urls = mutableListOf<String>()
        val config = PhotoServiceConfig(PhotoPlaceSource.AMAP, PhotoWeatherSource.AMAP, amapKey = "test&key")
        val result = PhotoWeatherSources.read(config, facts, now) { url ->
            urls += URLDecoder.decode(url, "UTF-8")
            when {
                "/coordinate/convert?" in url -> JSONObject("""{"status":"1","locations":"121.484,31.238"}""")
                "/geocode/regeo?" in url -> JSONObject("""{"status":"1","regeocode":{"addressComponent":{"province":"上海市","city":[],"district":"黄浦区","adcode":"310101"}}}""")
                "/weather/weatherInfo?" in url -> JSONObject("""{"status":"1","lives":[{"weather":"晴","temperature":"23.2","reporttime":"2026-11-02 11:50:00"}]}""")
                else -> error("Unexpected endpoint")
            }
        }
        assertEquals("晴 23°C", result)
        assertTrue(urls[0].contains("locations=121.48,31.23")); assertTrue(urls[0].contains("coordsys=gps"))
        assertTrue(urls[1].contains("location=121.484,31.238")); assertTrue(urls[2].contains("city=310101"))
    }

    @Test fun currentOnlySourcesNeverFetchForOldPhotosOrMissingKeys() = runTest {
        for (source in listOf(PhotoWeatherSource.AMAP, PhotoWeatherSource.SENIVERSE)) {
            var calls = 0
            val empty = PhotoServiceConfig(PhotoPlaceSource.SYSTEM, source)
            assertNull(PhotoWeatherSources.read(empty, facts, now) { calls++; null })
            val keyed = PhotoServiceConfig(PhotoPlaceSource.SYSTEM, source, "test", "test")
            assertNull(PhotoWeatherSources.read(keyed, facts.copy(capturedAt = now - 24 * 3600_000L), now) { calls++; null })
            assertEquals(0, calls)
        }
    }

    @Test fun seniverseUsesLatitudeLongitudeAndRejectsStaleOrInvalidObservations() = runTest {
        val response = JSONObject("""{"results":[{"now":{"text":"多云","temperature":"18"},"last_update":"2026-11-02T11:45:00+08:00"}]}""")
        val config = PhotoServiceConfig(PhotoPlaceSource.SYSTEM, PhotoWeatherSource.SENIVERSE, seniverseKey = "test")
        val result = PhotoWeatherSources.read(config, facts, now) { url ->
            assertTrue(URLDecoder.decode(url, "UTF-8").contains("location=31.23:121.48"))
            response
        }
        assertEquals("多云 18°C", result)
        val item = response.getJSONArray("results").getJSONObject(0)
        item.put("last_update", "2026-11-01T11:45:00+08:00")
        assertNull(PhotoWeatherSources.parseSeniverse(response, now, now))
        item.put("last_update", "2026-11-02T11:45:00+08:00").getJSONObject("now").put("temperature", "NaN")
        assertNull(PhotoWeatherSources.parseSeniverse(response, now, now))
        assertNull(PhotoWeatherSources.parseAmap(JSONObject("""{"status":"0"}"""), now, now))
    }

    @Test fun placeParsingHandlesEmptyArrayFieldsAndSettingsKeepSeparateCredentials() {
        val place = PhotoAmapLocation.parse(JSONObject("""{"status":"1","regeocode":{"addressComponent":{"province":"上海市","city":[],"district":"黄浦区","adcode":"310101"}}}"""))
        assertEquals("上海市 · 黄浦区", place?.name)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val settings = PhotoServiceSettings(app)
        settings.weatherSource = PhotoWeatherSource.SENIVERSE
        settings.placeSource = PhotoPlaceSource.AMAP
        settings.saveKey(PhotoWeatherSource.AMAP, "amap"); settings.saveKey(PhotoWeatherSource.SENIVERSE, "seniverse")
        val restored = PhotoServiceSettings(app).snapshot()
        assertEquals(PhotoWeatherSource.SENIVERSE, restored.weatherSource)
        assertEquals(PhotoPlaceSource.AMAP, restored.placeSource)
        assertEquals("amap", restored.amapKey); assertEquals("seniverse", restored.seniverseKey)
        assertThrows(IllegalArgumentException::class.java) { settings.saveLocation("错误", Double.NaN, 0.0) }
    }
}
