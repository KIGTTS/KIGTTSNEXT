package com.kigtts.next

import android.Manifest
import android.app.Application
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.WebAndroidPermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PhotoFactsPermissionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private open class Access(val allowed: Boolean) : PhotoFactsAccess {
        val calls = mutableListOf<String>()
        override suspend fun consent(kind: String): Boolean { calls += "consent/$kind"; return true }
        override suspend fun locationPermission(): Boolean { calls += "permission"; return allowed }
        override suspend fun currentLocation(): Location { calls += "locate"; return Location("test").apply { latitude = 22.3; longitude = 114.2 } }
        override suspend fun place(latitude: Double, longitude: Double): String { calls += "place"; return "拍摄地点" }
        override suspend fun weather(facts: FrozenPhotoFacts): String { calls += "weather"; return "晴" }
    }
    @Test fun locationPermissionMustBeGrantedBeforeReadingCurrentPosition() = runBlocking {
        val time = 1_700_000_000_000L
        val denied = Access(false)
        assertNull(PolaroidPhotoFacts.enrich(app, FrozenPhotoFacts(time, fromCamera = true), setOf(PhotoTextField.LOCATION), denied, time).location)
        assertEquals(listOf("consent/location", "permission"), denied.calls)
        val allowed = Access(true)
        val result = PolaroidPhotoFacts.enrich(app, FrozenPhotoFacts(time, fromCamera = true), setOf(PhotoTextField.LOCATION), allowed, time)
        assertEquals("拍摄地点", result.location)
        assertEquals(listOf("consent/location", "permission", "locate", "place"), allowed.calls)
    }
    @Test fun oldOrUnknownPhotographsNeverUseTodaysLocationAndFrozenFactsDoNotRefresh() = runBlocking {
        val access = Access(true)
        listOf(FrozenPhotoFacts(), FrozenPhotoFacts(1), FrozenPhotoFacts(2_000_000_000_000L)).forEach {
            val result = PolaroidPhotoFacts.enrich(app, it, setOf(PhotoTextField.LOCATION), access, 1_700_000_000_000L)
            assertNull(result.location)
        }
        val frozen = FrozenPhotoFacts(1, location = "旧地点")
        assertEquals(frozen, PolaroidPhotoFacts.enrich(app, frozen, setOf(PhotoTextField.LOCATION), access, 1_700_000_000_000L))
        assertTrue(access.calls.isEmpty())
    }
    @Test fun androidAdapterQueuesBothLocationPermissionsAndAcceptsApproximateChoice() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val request = async(start = CoroutineStart.UNDISPATCHED) { AndroidPhotoFactsAccess(app).locationPermission() }
        try {
            assertEquals("location", PhotoFactsConsent.pending.value?.kind)
            assertNull(WebAndroidPermissions.pending.value)
            requireNotNull(PhotoFactsConsent.pending.value).result.complete(true)
            yield()
            assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), WebAndroidPermissions.pending.value?.permissions)
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            WebAndroidPermissions.finish(mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to true, Manifest.permission.ACCESS_FINE_LOCATION to false))
            assertTrue(request.await())
        } finally { WebAndroidPermissions.cancel(); request.cancel() }
    }

    @Test fun decliningThePurposeExplanationDoesNotRequestLocationEvenIfSystemPermissionWasGranted() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val request = async(start = CoroutineStart.UNDISPATCHED) { AndroidPhotoFactsAccess(app).locationPermission() }
        try {
            assertEquals("location", PhotoFactsConsent.pending.value?.kind)
            assertNull(WebAndroidPermissions.pending.value)
            requireNotNull(PhotoFactsConsent.pending.value).result.complete(false)
            assertFalse(request.await())
            assertNull(WebAndroidPermissions.pending.value)
        } finally { request.cancel(); WebAndroidPermissions.cancel() }
    }

    @Test fun importedRecentPhotoWithoutGpsNeverUsesTheDevicesPosition() = runTest {
        val access = Access(true)
        val time = System.currentTimeMillis()
        val imported = FrozenPhotoFacts(time)
        assertEquals(imported, PolaroidPhotoFacts.enrich(app, imported, setOf(PhotoTextField.LOCATION, PhotoTextField.WEATHER), access, time))
        assertTrue(access.calls.isEmpty())
    }

    @Test fun rememberedCaptureFactsAreSavedWithoutBeingPresentInThePaperPreset() = runTest {
        app.getSharedPreferences("polaroid-facts", 0).edit().putBoolean("location", true).putBoolean("weather", true).commit()
        val time = System.currentTimeMillis()
        val access = Access(true)
        val result = PolaroidPhotoFacts.enrichCapture(app, FrozenPhotoFacts(time, fromCamera = true), emptySet(), access, time)
        assertEquals("拍摄地点", result.location); assertEquals("晴", result.weather)
        assertTrue(result.fromCamera)
        assertTrue(access.calls.containsAll(listOf("locate", "place", "weather")))
    }

    @Test fun oldExifCoordinatesStillResolveWithoutRequestingDeviceLocation() = runTest {
        val access = Access(false)
        val result = PolaroidPhotoFacts.enrich(app, FrozenPhotoFacts(1, latitude = 31.23, longitude = 121.47),
            setOf(PhotoTextField.LOCATION, PhotoTextField.WEATHER), access)
        assertEquals("拍摄地点", result.location); assertEquals("晴", result.weather)
        assertFalse(access.calls.any { it in listOf("locate", "permission") })
    }

    @Test fun twoBlockedLegacyGeocodersCannotStarveTheWeatherQueue() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        val jobs = (1..2).map { async(start = CoroutineStart.UNDISPATCHED) { PhotoFactsWork.run(2000) {
            entered.countDown()
            while (release.count > 0) { try { release.await() } catch (_: InterruptedException) { } }
            "late"
        } } }
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("weather", PhotoFactsWork.network(1000) { "weather" })
            jobs.awaitAll().forEach { assertNull(it) }
        } finally { release.countDown() }
    }

    @Test fun stalledQueriesFinishTogetherAndUnavailableFieldsStayVisibleAsUnknown() = runTest {
        val access = object : Access(true) {
            override suspend fun place(latitude: Double, longitude: Double): String = awaitCancellation()
            override suspend fun weather(facts: FrozenPhotoFacts): String = awaitCancellation()
        }
        val start = testScheduler.currentTime
        val facts = FrozenPhotoFacts(1, latitude = 22.3, longitude = 114.2)
        val result = PolaroidPhotoFacts.enrich(app, facts, setOf(PhotoTextField.LOCATION, PhotoTextField.WEATHER), access)
        assertEquals(PhotoFactsWork.TIMEOUT_MS, testScheduler.currentTime - start)
        assertEquals("未知", result.text(PhotoTextField.LOCATION))
        assertEquals("未知", result.text(PhotoTextField.WEATHER))
        assertEquals(facts.capturedAt, result.capturedAt)
    }

    @Test fun geocoderFailureDoesNotDiscardWeatherOrFreezeCoordinatesAsAPlaceName() = runTest {
        val access = object : Access(true) {
            override suspend fun place(latitude: Double, longitude: Double): String = error("offline")
        }
        val result = PolaroidPhotoFacts.enrich(app, FrozenPhotoFacts(1, latitude = 22.3, longitude = 114.2),
            setOf(PhotoTextField.LOCATION, PhotoTextField.WEATHER), access)
        assertEquals("未知", result.text(PhotoTextField.LOCATION))
        assertEquals("晴", result.weather)
    }

    @Test fun blockingRomCallCannotHoldTheCallerPastItsDeadline() = runBlocking {
        val release = java.util.concurrent.CountDownLatch(1)
        val started = System.nanoTime()
        try {
            val result = withTimeout(1500) { PhotoFactsWork.run(80) {
                while (release.count > 0) { try { release.await() } catch (_: InterruptedException) { } }
                "late result"
            } }
            assertNull(result)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1000)
        } finally { release.countDown() }
    }
}
