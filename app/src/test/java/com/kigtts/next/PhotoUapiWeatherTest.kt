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
import java.util.Locale

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PhotoUapiWeatherTest {
    private val now = Instant.parse("2026-11-04T04:00:00Z").toEpochMilli()
    private val facts get() = FrozenPhotoFacts(now, latitude = 31.23, longitude = 121.47)
    private val config = PhotoServiceConfig(PhotoPlaceSource.OFFLINE, PhotoWeatherSource.UAPI)
    private fun observation(time: String = "11 分钟前发布") = JSONObject().put("city", "上海")
        .put("weather", "小雨").put("temperature", 18).put("report_time", time)

    @Test fun anonymousRequestAlwaysCarriesThePhotoCityAndNoCredentials() = runTest {
        val result = PhotoWeatherSources.read(config, facts, now, city = "上海") { url ->
            assertEquals("https://uapis.cn/api/v1/misc/weather?city=上海&lang=zh", URLDecoder.decode(url, "UTF-8"))
            observation()
        }
        assertEquals("小雨 18°C", result)
        assertFalse(PhotoWeatherSource.UAPI.requiresKey)
    }

    @Test fun missingCityOldPhotoOrInvalidCoordinatesNeverUseIpWeather() = runTest {
        var calls = 0
        val fetch: PhotoJsonFetch = { calls++; observation() }
        assertNull(PhotoWeatherSources.read(config, facts, now, fetch = fetch))
        assertNull(PhotoWeatherSources.read(config, facts, now, city = "  ", fetch = fetch))
        assertNull(PhotoWeatherSources.read(config, facts.copy(capturedAt = now - 24 * 3600_000L), now, city = "上海", fetch = fetch))
        assertNull(PhotoWeatherSources.read(config, facts.copy(latitude = null), now, city = "上海", fetch = fetch))
        assertNull(PhotoWeatherSources.read(config, facts.copy(capturedAt = null), now, city = "上海", fetch = fetch))
        assertEquals(0, calls)
    }

    @Test fun relativeAndAbsoluteReportTimesAcceptFreshObservations() {
        for (time in listOf("刚刚", "刚刚发布", "30秒前更新", "1 小时前发布", "2026-11-04 11:45:00", "2026-11-04T11:45:00+08:00")) {
            assertEquals(time, "小雨 18°C", PhotoUapiWeather.parse(observation(time), now, now))
        }
    }

    @Test fun malformedStaleFutureOrWrongCaptureObservationsStayUnknown() {
        for (time in listOf("", "unknown", "4小时前发布", "2026-11-03 12:00:00", "2026-11-04T13:00:00+08:00")) {
            assertNull(time, PhotoUapiWeather.parse(observation(time), now, now))
        }
        assertNull(PhotoUapiWeather.parse(observation(), now - 24 * 3600_000L, now))
        assertNull(PhotoUapiWeather.parse(observation("3小时前"), now + 600_000L, now))
    }

    @Test fun failedOrIncompleteResponsesNeverBecomePhotoCaptions() = runTest {
        assertNull(PhotoWeatherSources.read(config, facts, now, city = "上海") { null })
        assertNull(PhotoUapiWeather.parse(JSONObject("""{"code":500,"message":"unavailable"}"""), now, now))
        for (field in listOf("city", "weather", "temperature", "report_time")) {
            assertNull(field, PhotoUapiWeather.parse(observation().also { it.remove(field) }, now, now))
        }
        for (temperature in listOf("NaN", "Infinity", "90", "null")) {
            assertNull(PhotoUapiWeather.parse(observation().put("temperature", temperature), now, now))
        }
    }

    @Test fun cityLookupUsesPhotoCoordinatesAndPreservesNearbyDisplayLabels() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("黄浦", PhotoPlaceIndex.city(app, 31.23, 121.47))
        assertEquals("黄浦 附近", PhotoPlaceIndex.nearby(app, 31.23, 121.47, Locale.CHINESE))
        assertEquals("Near Huangpu", PhotoPlaceIndex.nearby(app, 31.23, 121.47, Locale.ENGLISH))
        assertEquals("上海", PhotoPlaceIndex.city(app, 31.22222, 121.45806))
        assertNull(PhotoPlaceIndex.city(app, 0.0, 0.0))
        assertNull(PhotoPlaceIndex.city(app, Double.NaN, 121.47))
    }
}
