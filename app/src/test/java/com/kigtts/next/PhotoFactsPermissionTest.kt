package com.kigtts.next

import android.Manifest
import android.app.Application
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.WebAndroidPermissions
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PhotoFactsPermissionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private class Access(val allowed: Boolean) : PhotoFactsAccess {
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
        assertNull(PolaroidPhotoFacts.enrich(app, FrozenPhotoFacts(time), setOf(PhotoTextField.LOCATION), denied, time).location)
        assertEquals(listOf("consent/location", "permission"), denied.calls)
        val allowed = Access(true)
        val result = PolaroidPhotoFacts.enrich(app, FrozenPhotoFacts(time), setOf(PhotoTextField.LOCATION), allowed, time)
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
    @Test fun androidAdapterQueuesTheRealCoarseLocationRuntimePermission() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val request = async(start = CoroutineStart.UNDISPATCHED) { AndroidPhotoFactsAccess(app).locationPermission() }
        try {
            assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), WebAndroidPermissions.pending.value?.permissions)
            WebAndroidPermissions.finish(mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to false))
            assertFalse(request.await())
        } finally { WebAndroidPermissions.cancel() }
    }
}
