package com.kigtts.next

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class) @Config(sdk = [26,35])
class PhotoCaptureLocationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Test fun aFreshGpsCacheWorksEvenWhenNetworkHasNoFix() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = app.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled("gps",true)
        val gps = Location("gps").apply { latitude=31.23;longitude=121.47;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos() }
        shadowOf(manager).setLastKnownLocation("gps",gps)
        val result = PhotoCaptureLocation(app).current()
        assertNotNull(result);assertEquals(gps.latitude,result!!.latitude,0.0)
    }
    @Test fun sourceMarkerSurvivesRestartWithoutChangingLegacyPhotographs() {
        val root = java.io.File(app.cacheDir,"photo-origin").apply { mkdirs() }
        val facts = FrozenPhotoFacts(1234,fromCamera=true)
        PolaroidDesignStore(root).freeze("camera",facts)
        assertEquals(facts,PolaroidDesignStore(root).facts("camera"))
        assertFalse(PolaroidDesignStore(root).facts("missing").fromCamera)
    }
    @Test fun staleFutureOrInvalidFixesAreRejectedAndOfflinePlacesAreExplicitlyApproximate() {
        val fix = Location("network").apply { latitude=31.23;longitude=121.47;time=1000 }
        assertTrue(photoLocationIsFresh(fix,2000,0))
        assertFalse(photoLocationIsFresh(fix,122000,0));assertFalse(photoLocationIsFresh(fix,0,0))
        fix.latitude=Double.NaN;assertFalse(photoLocationIsFresh(fix,2000,0))
        val nearby = PhotoPlaceIndex.nearby(app,31.22222,121.45806,Locale.SIMPLIFIED_CHINESE)
        assertEquals("上海 附近",nearby)
        assertNull(PhotoPlaceIndex.nearby(app,0.0,0.0))
        assertNull(PhotoPlaceIndex.nearby(app,Double.NaN,121.47))
    }
}
